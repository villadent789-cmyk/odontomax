package com.odontomax.agenda.application.port.in;

import com.odontomax.agenda.application.dto.response.BandejaTareasResponseDTO;

import java.time.LocalDate;

public interface ConsultarBandejaTareasUseCase {
    BandejaTareasResponseDTO obtenerBandejaTareas(String sedeId, LocalDate fecha);
}
