package com.odontomax.pacientes.application.port.in;

import com.odontomax.pacientes.api.PacienteResumen;
import com.odontomax.pacientes.application.dto.response.PacienteResponseDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface ConsultarPacienteUseCase {

    /**
     * Lista los pacientes activos de la sede efectiva del usuario (la resuelve el caso de uso), filtrados por el término
     * si viene informado, con la fecha de la última cita atendida completada.
     */
    List<PacienteResponseDTO> listar(String busqueda, String sedeId);

    /** Igual que {@link #listar} pero paginado; el orden lo define el {@link Pageable}. */
    Page<PacienteResponseDTO> listarPaginado(String busqueda, String sedeId, Pageable pageable);

    /** Pacientes con consentimiento de promociones que cumplen años mañana (hora de Lima); sedeId null = todas las sedes. */
    List<PacienteResumen> listarCumpleanerosDeManana(String sedeId);

    /** Paciente por id; valida que el usuario tenga acceso a su sede. */
    PacienteResponseDTO obtenerPorId(UUID id);
}
