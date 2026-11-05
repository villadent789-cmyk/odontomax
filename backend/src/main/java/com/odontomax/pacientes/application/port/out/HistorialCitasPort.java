package com.odontomax.pacientes.application.port.out;

import com.odontomax.application.dto.response.CitaResponseDTO;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Punto único de contacto de pacientes con la agenda (DT-002). */
public interface HistorialCitasPort {

    /** Fecha de la última cita ATENDIDA de cada paciente, en una sola consulta agrupada. */
    Map<UUID, LocalDate> ultimasAtenciones(Collection<UUID> pacienteIds);

    /** Citas del paciente ordenadas por fecha descendente. */
    List<CitaResponseDTO> citasDePaciente(UUID pacienteId);

    /** Lanza AccessDenied si el usuario es odontólogo y no tiene cita con el paciente. */
    void exigirAccesoDeOdontologo(UUID pacienteId);
    /** B�squeda de pacientes asignados al odont�logo con cita. */
    List<com.odontomax.pacientes.application.dto.response.PacienteResponseDTO> buscarPacientesOdontologo(String termino, String sedeId, UUID usuarioId);

    /** B�squeda paginada de pacientes asignados al odont�logo con cita. */
    org.springframework.data.domain.Page<com.odontomax.pacientes.application.dto.response.PacienteResponseDTO> buscarPacientesOdontologoPaginado(
            String termino, String sedeId, UUID usuarioId, org.springframework.data.domain.Pageable pageable);
}