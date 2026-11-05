package com.odontomax.pacientes.application.port.out;

import com.odontomax.application.dto.response.NotaEvolucionResponseDTO;
import com.odontomax.application.dto.response.OdontogramaRegistroResponseDTO;
import com.odontomax.application.dto.response.PagoResponseDTO;
import com.odontomax.application.dto.response.TratamientoResponseDTO;

import java.util.List;
import java.util.UUID;

public interface ExpedienteSeccionesPort {
    List<TratamientoResponseDTO> listarTratamientosPorPaciente(UUID pacienteId);
    List<PagoResponseDTO> listarPagosPorPaciente(UUID pacienteId);
    List<NotaEvolucionResponseDTO> listarNotasPorPaciente(UUID pacienteId);
    List<OdontogramaRegistroResponseDTO> obtenerOdontogramaPorPaciente(UUID pacienteId);
}