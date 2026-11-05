package com.odontomax.identidad.infrastructure.adapter;

import com.odontomax.identidad.application.port.out.JwtUserCachePort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caché en memoria (por instancia) de usuarios del filtro JWT, con validez de 60 s por entrada.
 *
 * Al desalojar por un cambio de credenciales se guarda una marca con la versión vigente: una petición que leyó el
 * usuario de la BD antes del cambio no puede volver a cachear la versión anterior después del desalojo.
 */
@Component
public class InMemoryJwtUserCacheAdapter implements JwtUserCachePort {

    private static final long CACHE_DURATION_MS = 60_000L; // 60 segundos
    /** Una lectura de BD anterior al cambio llega al put en milisegundos; 2 min de marca sobran. */
    private static final long RETENCION_MARCA_MS = 2 * 60_000L;
    static final int MAX_MARCAS = 100_000;

    private final Map<String, CachedUserInfo> userCache = new ConcurrentHashMap<>();
    private final Map<String, Marca> versionMinima = new ConcurrentHashMap<>();

    private record CachedUserInfo(UsuarioAutenticado usuario, long cachedAt) {
        boolean isExpired() {
            return System.currentTimeMillis() - cachedAt > CACHE_DURATION_MS;
        }
    }

    private record Marca(int version, long registradaEn) {}

    @Override
    public Optional<UsuarioAutenticado> get(String emailLower) {
        CachedUserInfo cached = userCache.get(emailLower);
        if (cached != null && !cached.isExpired()) {
            return Optional.of(cached.usuario());
        }
        return Optional.empty();
    }

    @Override
    public void put(String emailLower, UsuarioAutenticado usuario) {
        // Comprobación de la marca y escritura en un único compute sobre la clave: desalojar() también opera dentro de
        // userCache.compute para la misma clave, así que ambas operaciones quedan serializadas (revisión 2, obs. 1)
        userCache.compute(emailLower, (k, actual) -> {
            Marca marca = versionMinima.get(k);
            if (marca != null && usuario.tokenVersion() < marca.version()) {
                return actual; // lectura anterior a un cambio de credenciales: no se cachea
            }
            return new CachedUserInfo(usuario, System.currentTimeMillis());
        });
    }

    @Override
    public void evict(String emailLower) {
        userCache.remove(emailLower);
    }

    @Override
    public void desalojar(String emailLower, int versionVigente) {
        if (!versionMinima.containsKey(emailLower) && versionMinima.size() >= MAX_MARCAS) {
            purgarMarcasCaducadas();
            if (versionMinima.size() >= MAX_MARCAS) {
                // Sin sitio para la marca: se vacía la caché entera (solo cuesta lecturas de BD) y no se arriesga nada
                userCache.clear();
                return;
            }
        }
        long ahora = System.currentTimeMillis();
        userCache.compute(emailLower, (k, actual) -> {
            versionMinima.merge(k, new Marca(versionVigente, ahora),
                    (vieja, nueva) -> nueva.version() >= vieja.version() ? nueva : new Marca(vieja.version(), ahora));
            return null; // desaloja
        });
    }

    @Scheduled(fixedRate = 60000)
    public void purgarMarcasCaducadas() {
        long ahora = System.currentTimeMillis();
        versionMinima.entrySet().removeIf(e -> ahora - e.getValue().registradaEn() > RETENCION_MARCA_MS);
    }
}
