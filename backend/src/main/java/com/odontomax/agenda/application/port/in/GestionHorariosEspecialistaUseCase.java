package com.odontomax.agenda.application.port.in;

import com.odontomax.agenda.application.dto.request.HorarioEspecialistaRequestDTO;
import com.odontomax.agenda.application.dto.response.HorarioEspecialistaResponseDTO;

import java.util.List;
import java.util.UUID;

public interface GestionHorariosEspecialistaUseCase {
    List<HorarioEspecialistaResponseDTO> obtenerHorarios(UUID especialistaId);
    List<HorarioEspecialistaResponseDTO> reemplazarHorarios(UUID especialistaId, List<HorarioEspecialistaRequestDTO> horarios);
}
