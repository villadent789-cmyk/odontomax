package com.odontomax.agenda.application.port.in;

import com.odontomax.agenda.application.dto.response.CitaResponseDTO;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ConsultarCitaUseCase {
    CitaResponseDTO obtenerPorId(UUID citaId);
    List<CitaResponseDTO> obtenerCitasDelDia(LocalDate fecha, String sedeId);
    List<CitaResponseDTO> obtenerCitasPorRango(LocalDate desde, LocalDate hasta, String sedeId);
    List<CitaResponseDTO> obtenerReservasWebPendientes(String sedeId);
    List<CitaResponseDTO> obtenerListaEspera(String sedeId);
}
