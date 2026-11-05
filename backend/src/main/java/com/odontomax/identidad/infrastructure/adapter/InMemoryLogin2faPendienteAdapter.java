package com.odontomax.identidad.infrastructure.adapter;

import com.odontomax.identidad.application.port.out.Login2faPendientePort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Logins pendientes de 2FA en memoria (por instancia). Una entrada se crea, como pronto, al emitirse su token
 * temporal (válido 5 min), así que se purga a los 5 min. Tamaño acotado: si se llena y la purga no libera sitio,
 * los logins pendientes nuevos se rechazan (falla cerrado).
 *
 * Falla cerrado sin abrir una denegación de servicio (revisión 2, obs. 2): {@code LoginService} solo crea una
 * entrada después de reservar el intento en el límite por cuenta (10 cada 15 min) y en el de correo + IP, así que
 * incluso con la contraseña cada cuenta con 2FA genera como mucho 10 entradas por ventana y llenar 100 000 exigiría
 * miles de cuentas comprometidas. Desalojar en vez de rechazar sería peor: borraría el estado "ya usado" de un token
 * y reabriría su cupo.
 */
@Component
public class InMemoryLogin2faPendienteAdapter implements Login2faPendientePort {

    private static final long RETENCION_MS = 5 * 60 * 1000L; // vida del token temporal
    static final int MAX_ENTRADAS_POR_DEFECTO = 100_000;

    private final Map<String, Estado> pendientes = new ConcurrentHashMap<>();
    private final int maxEntradas;

    public InMemoryLogin2faPendienteAdapter() {
        this(MAX_ENTRADAS_POR_DEFECTO);
    }

    InMemoryLogin2faPendienteAdapter(int maxEntradas) {
        this.maxEntradas = maxEntradas;
    }

    @Override
    public boolean intentoRechazado(String clave, int maximo) {
        if (!hayHueco(clave)) {
            return true;
        }
        long ahora = System.currentTimeMillis();
        boolean[] rechazado = {false};
        pendientes.compute(clave, (k, v) -> {
            Estado actual = v != null ? v : new Estado(0, false, ahora);
            if (actual.invalidado() || actual.intentos() >= maximo) {
                rechazado[0] = true;
                return actual;
            }
            return new Estado(actual.intentos() + 1, false, actual.creadoEn());
        });
        return rechazado[0];
    }

    @Override
    public int intentosReservados(String clave) {
        Estado e = pendientes.get(clave);
        return e != null ? e.intentos() : 0;
    }

    @Override
    public boolean invalidar(String clave) {
        long ahora = System.currentTimeMillis();
        boolean[] yaInvalidado = {false};
        pendientes.compute(clave, (k, v) -> {
            if (v == null) {
                return new Estado(0, true, ahora);
            }
            yaInvalidado[0] = v.invalidado();
            return new Estado(v.intentos(), true, v.creadoEn());
        });
        return yaInvalidado[0];
    }

    private boolean hayHueco(String clave) {
        if (pendientes.containsKey(clave) || pendientes.size() < maxEntradas) {
            return true;
        }
        purgarCaducados();
        return pendientes.size() < maxEntradas;
    }

    @Scheduled(fixedRate = 60000)
    public void purgarCaducados() {
        long ahora = System.currentTimeMillis();
        pendientes.entrySet().removeIf(e -> ahora - e.getValue().creadoEn() > RETENCION_MS);
    }

    private record Estado(int intentos, boolean invalidado, long creadoEn) {}
}
