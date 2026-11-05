package com.odontomax.pacientes.application.port.in;

import com.odontomax.pacientes.application.dto.request.PacienteRequestDTO;
import com.odontomax.pacientes.application.dto.response.PacienteResponseDTO;

public interface RegistrarPacienteUseCase {

    PacienteResponseDTO registrar(PacienteRequestDTO request);
}
