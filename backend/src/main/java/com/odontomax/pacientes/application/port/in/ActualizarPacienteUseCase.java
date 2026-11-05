package com.odontomax.pacientes.application.port.in;

import com.odontomax.pacientes.application.dto.request.PacienteRequestDTO;
import com.odontomax.pacientes.application.dto.response.PacienteResponseDTO;

import java.util.UUID;

public interface ActualizarPacienteUseCase {

    PacienteResponseDTO actualizar(UUID id, PacienteRequestDTO request);
}
