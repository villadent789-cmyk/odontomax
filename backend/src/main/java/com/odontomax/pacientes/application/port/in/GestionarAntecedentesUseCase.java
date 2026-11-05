package com.odontomax.pacientes.application.port.in;

import com.odontomax.pacientes.application.dto.request.AntecedentesUpdateRequestDTO;
import com.odontomax.pacientes.application.dto.response.AntecedenteHistorialResponseDTO;
import com.odontomax.pacientes.application.dto.response.PacienteResponseDTO;

import java.util.List;
import java.util.UUID;

public interface GestionarAntecedentesUseCase {
    List<AntecedenteHistorialResponseDTO> obtenerHistorial(UUID pacienteId);
    PacienteResponseDTO actualizarAntecedentes(UUID pacienteId, AntecedentesUpdateRequestDTO dto);
}