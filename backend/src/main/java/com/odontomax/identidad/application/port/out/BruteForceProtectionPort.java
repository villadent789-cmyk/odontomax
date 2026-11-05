package com.odontomax.identidad.application.port.out;

import java.util.OptionalLong;

/** Estado anti fuerza bruta del login (clave {@code email|ip}). */
public interface BruteForceProtectionPort {

    /**
     * Minutos de bloqueo restantes (mínimo 1) si la clave está bloqueada; vacío si no lo está.
     * Si el bloqueo ya expiró, elimina la entrada.
     */
    OptionalLong minutosDeBloqueoRestantes(String key);

    void recordFailedAttempt(String key);

    void resetAttempts(String key);

    /**
     * Reserva atómica de un intento ANTES de verificar un código (2FA): si la clave está bloqueada devuelve los
     * minutos restantes y no cuenta nada; si no, cuenta ya el intento como fallido (se borra con
     * {@link #resetAttempts} si el código resulta correcto). Así N peticiones concurrentes no superan el límite.
     */
    OptionalLong reservarIntento(String key);

    /** Igual que {@link #reservarIntento(String)} con un máximo propio para la clave (misma ventana de 15 min). */
    OptionalLong reservarIntento(String key, int maximo);
}
