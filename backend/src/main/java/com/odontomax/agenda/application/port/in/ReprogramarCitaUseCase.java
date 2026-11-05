package com.odontomax.agenda.application.port.in;

import com.odontomax.agenda.application.dto.request.CitaReprogramarRequestDTO;
import com.odontomax.agenda.application.dto.response.CitaResponseDTO;

import java.util.UUID;

public interface ReprogramarCitaUseCase {
    CitaResponseDTO reprogramarCita(UUID citaId, CitaReprogramarRequestDTO request);
}
