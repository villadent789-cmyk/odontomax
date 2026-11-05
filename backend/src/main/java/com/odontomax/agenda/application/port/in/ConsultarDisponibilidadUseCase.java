package com.odontomax.agenda.application.port.in;

import com.odontomax.agenda.application.dto.response.DisponibilidadResponseDTO;

import java.time.LocalDate;
import java.util.UUID;

public interface ConsultarDisponibilidadUseCase {
    DisponibilidadResponseDTO obtenerDisponibilidad(UUID especialistaId, LocalDate fecha, String sedeId);
}
