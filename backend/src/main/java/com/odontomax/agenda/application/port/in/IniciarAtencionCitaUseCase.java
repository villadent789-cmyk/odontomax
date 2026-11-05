package com.odontomax.agenda.application.port.in;

import com.odontomax.agenda.application.dto.response.CitaResponseDTO;

import java.util.UUID;

public interface IniciarAtencionCitaUseCase {
    CitaResponseDTO iniciarAtencion(UUID citaId);
}
