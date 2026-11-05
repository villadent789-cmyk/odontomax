package com.odontomax.agenda.api;

import java.util.UUID;

/** Interfaz pública de la agenda para reaccionar a un cobro registrado sobre un plan de tratamiento. */
public interface CobroDeCitaModuloApi {

    /**
     * Avisa que se registró un cobro del plan. Si el plan pertenece a una cita de Villamax ya atendida y el cobro quedó
     * completo, nace la comisión del doctor derivador (idempotente). Un fallo aquí nunca debe deshacer el cobro.
     */
    void alRegistrarseCobro(UUID tratamientoId);
}
