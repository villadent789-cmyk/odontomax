package com.odontomax.agenda.api;

import com.odontomax.application.dto.response.CitaResponseDTO;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ConsultaCitasModuloApi {

    /**
     * Devuelve un mapa con la fecha de la última cita ATENDIDA por cada paciente en la colección.
     */
    Map<UUID, LocalDate> findUltimaAtencionPorPaciente(Collection<UUID> pacienteIds);

    /**
     * Devuelve el historial de citas del paciente ordenadas por fecha descendente.
     */
    List<CitaResponseDTO> findByPacienteIdOrderByFechaDesc(UUID pacienteId);

    /**
     * Devuelve las citas entre dos fechas (inclusive) para una sede (o todas si sedeId es null).
     */
    List<CitaResponseDTO> findByRangoFechasYSede(LocalDate desde, LocalDate hasta, String sedeId);
}
