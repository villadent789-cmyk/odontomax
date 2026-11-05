package com.odontomax.agenda.application.port.in;

import com.odontomax.agenda.application.dto.request.RegistrarContactoRequestDTO;
import com.odontomax.agenda.application.dto.response.CitaResponseDTO;

import java.util.UUID;

public interface RegistrarContactoUseCase {
    CitaResponseDTO registrarContacto(UUID citaId, RegistrarContactoRequestDTO request);
}
