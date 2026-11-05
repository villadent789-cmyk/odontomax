package com.odontomax.pacientes.infrastructure.adapter;

import com.odontomax.application.dto.response.NotaEvolucionResponseDTO;
import com.odontomax.application.dto.response.OdontogramaRegistroResponseDTO;
import com.odontomax.application.dto.response.PagoResponseDTO;
import com.odontomax.application.dto.response.TratamientoResponseDTO;
import com.odontomax.application.service.NotaEvolucionService;
import com.odontomax.application.service.OdontogramaService;
import com.odontomax.application.service.PagoService;
import com.odontomax.pacientes.application.port.out.ExpedienteSeccionesPort;
import com.odontomax.tratamientos.service.TratamientoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ExpedienteLegacyAdapter implements ExpedienteSeccionesPort {

    private final TratamientoService tratamientoService;
    private final PagoService pagoService;
    private final NotaEvolucionService notaEvolucionService;
    private final OdontogramaService odontogramaService;

    @Override
    public List<TratamientoResponseDTO> listarTratamientosPorPaciente(UUID pacienteId) {
        return tratamientoService.listarPorPaciente(pacienteId);
    }

    @Override
    public List<PagoResponseDTO> listarPagosPorPaciente(UUID pacienteId) {
        return pagoService.listarPorPaciente(pacienteId);
    }

    @Override
    public List<NotaEvolucionResponseDTO> listarNotasPorPaciente(UUID pacienteId) {
        return notaEvolucionService.listarPorPaciente(pacienteId);
    }

    @Override
    public List<OdontogramaRegistroResponseDTO> obtenerOdontogramaPorPaciente(UUID pacienteId) {
        return odontogramaService.obtenerEstadoActual(pacienteId);
    }
}