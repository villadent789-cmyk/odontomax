package com.odontomax.agenda.application.port.in;

import com.odontomax.agenda.application.dto.response.CitaResponseDTO;

import java.util.UUID;

public interface CambiarEstadoCitaUseCase {
    CitaResponseDTO cambiarEstado(UUID citaId, String nuevoEstado, String motivo);

    /**
     * Cambia el estado; al marcar ATENDIDA una cita de un doctor invitado se exige el monto de sus honorarios, que sale
     * como egreso de caja.
     */
    default CitaResponseDTO cambiarEstado(UUID citaId, String nuevoEstado, String motivo,
                                          java.math.BigDecimal montoHonorario, String metodoPagoHonorario) {
        return cambiarEstado(citaId, nuevoEstado, motivo);
    }
}
