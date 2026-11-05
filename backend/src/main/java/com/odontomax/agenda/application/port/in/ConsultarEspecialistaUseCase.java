package com.odontomax.agenda.application.port.in;

import com.odontomax.agenda.application.dto.response.EspecialistaResponseDTO;

import java.util.List;

public interface ConsultarEspecialistaUseCase {
    List<EspecialistaResponseDTO> listarEspecialistas(String sedeId);
    List<EspecialistaResponseDTO> listarTodos();
    List<EspecialistaResponseDTO> listarPublicos();
    List<EspecialistaResponseDTO> listarPublicosPorSede(String sedeId);
}
