package com.odontomax.agenda.application.port.in;

import com.odontomax.agenda.application.dto.request.RegistrarAdelantoRequestDTO;
import com.odontomax.agenda.application.dto.response.CitaResponseDTO;

import java.util.UUID;

public interface RegistrarAdelantoUseCase {
    CitaResponseDTO registrarAdelanto(UUID citaId, RegistrarAdelantoRequestDTO request);
}
