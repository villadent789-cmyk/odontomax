package com.odontomax.agenda.application.port.out;

import java.util.UUID;

public interface ComisionCitaPort {

    /**
     * Genera automáticamente la comisión radiológica en estado PENDIENTE para la cita indicada
     * si pertenece a Villamax, tiene doctor derivador (no es paciente directo), está cobrada por completo
     * y aún no ha sido registrada (idempotente).
     */
    void generarComisionSiAplica(UUID citaId);

    /**
     * Si la cita de Villamax tenía una comisión pendiente, la pasa a estado ANULADO.
     */
    void anularComisionSiAplica(UUID citaId, String motivo);

    /**
     * Anula cualquier comisión pendiente asociada a un tratamiento cuyo cobro fue anulado.
     */
    void anularComisionPorTratamientoSiAplica(UUID tratamientoId, String motivo);
}
