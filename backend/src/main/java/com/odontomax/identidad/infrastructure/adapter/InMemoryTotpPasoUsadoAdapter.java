package com.odontomax.identidad.infrastructure.adapter;

import com.odontomax.identidad.application.port.out.TotpPasoUsadoPort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Último paso TOTP usado por usuario, en memoria (por instancia). Un código solo se acepta en su paso y en los
 * contiguos (±30 s): pasados 2 min desde su uso ya no es válido y la entrada se puede purgar. Tamaño acotado: si se
 * llena y la purga no libera sitio, se trata como reutilización (falla cerrado; quedan los códigos de respaldo).
 */
@Component
public class InMemoryTotpPasoUsadoAdapter implements TotpPasoUsadoPort {

    private static final long RETENCION_MS = 2 * 60 * 1000L;
    static final int MAX_ENTRADAS = 100_000;

    private final Map<UUID, Uso> ultimos = new ConcurrentHashMap<>();

    @Override
    public boolean esReutilizacion(UUID usuarioId, long paso) {
        if (!ultimos.containsKey(usuarioId) && ultimos.size() >= MAX_ENTRADAS) {
            purgarCaducados();
            if (ultimos.size() >= MAX_ENTRADAS) {
                return true;
            }
        }
        long ahora = System.currentTimeMillis();
        boolean[] reutilizado = {false};
        ultimos.compute(usuarioId, (k, v) -> {
            if (v != null && paso <= v.paso()) {
                reutilizado[0] = true;
                return v;
            }
            return new Uso(paso, ahora);
        });
        return reutilizado[0];
    }

    @Override
    public void olvidar(UUID usuarioId) {
        ultimos.remove(usuarioId);
    }

    @Scheduled(fixedRate = 60000)
    public void purgarCaducados() {
        long ahora = System.currentTimeMillis();
        ultimos.entrySet().removeIf(e -> ahora - e.getValue().registradoEn() > RETENCION_MS);
    }

    private record Uso(long paso, long registradoEn) {}
}
