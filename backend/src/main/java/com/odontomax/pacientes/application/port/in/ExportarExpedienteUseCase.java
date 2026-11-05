package com.odontomax.pacientes.application.port.in;

import com.odontomax.pacientes.application.dto.response.PacienteExportDTO;

import java.util.UUID;

public interface ExportarExpedienteUseCase {
    PacienteExportDTO exportarExpedienteCompleto(UUID pacienteId);
}