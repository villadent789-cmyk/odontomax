package com.odontomax.pacientes.application.port.out;

import com.odontomax.pacientes.domain.model.AntecedenteHistorial;

import java.util.List;
import java.util.UUID;

public interface AntecedenteHistorialRepositoryPort {
    List<AntecedenteHistorial> findByPacienteIdOrderByCreadoEnDesc(UUID pacienteId);
    AntecedenteHistorial save(AntecedenteHistorial historial);
}