package com.odontomax.agenda.application.port.out;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Lo que la agenda necesita del módulo de tratamientos para enlazar una cita con una sesión de un plan:
 * consultarlo al agendar y sumarle la sesión cuando la cita se atiende.
 */
public interface TratamientoCitaPort {

    Optional<TratamientoVinculable> buscar(UUID tratamientoId);

    UUID crearPlanParaAdelanto(UUID pacienteId, String sedeId, UUID especialistaId, String motivo, BigDecimal monto);

    void validarSaldoParaCobro(UUID pacienteId, UUID tratamientoId, BigDecimal monto);

    /** Suma una sesión realizada al plan por la cita atendida. */
    void registrarSesionAtendida(UUID tratamientoId, UUID citaId);

    record TratamientoVinculable(UUID id, UUID pacienteId, String estado, String nombre) {

        /** Solo se agenda contra un plan que sigue abierto. */
        public boolean admiteNuevasSesiones() {
            return "PLANIFICADO".equalsIgnoreCase(estado) || "EN_PROCESO".equalsIgnoreCase(estado);
        }
    }
}
