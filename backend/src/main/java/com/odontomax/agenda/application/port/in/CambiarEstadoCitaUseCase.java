package com.odontomax.agenda.application.port.in;

import com.odontomax.agenda.application.dto.response.CitaResponseDTO;

import java.util.UUID;

public interface CambiarEstadoCitaUseCase {
    CitaResponseDTO cambiarEstado(UUID citaId, String nuevoEstado, String motivo);
}
