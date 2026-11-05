package com.odontomax.identidad.application.port.out;

import java.util.UUID;

/** Último paso de tiempo TOTP (RFC 6238, 30 s) aceptado por usuario, para impedir reutilizar un código. */
public interface TotpPasoUsadoPort {

    /**
     * Comprueba y registra de forma atómica el uso de un código del paso {@code paso}.
     *
     * @return {@code true} si es una reutilización (paso menor o igual que el último aceptado) y debe rechazarse;
     * {@code false} si el paso es nuevo, en cuyo caso queda registrado como el último usado
     */
    boolean esReutilizacion(UUID usuarioId, long paso);

    /** Olvida el último paso del usuario (al configurar un secreto nuevo, los pasos del anterior no aplican). */
    void olvidar(UUID usuarioId);
}
