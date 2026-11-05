package com.odontomax.pacientes.infrastructure.adapter;

import com.odontomax.application.dto.response.*;
import com.odontomax.application.service.NotaEvolucionService;
import com.odontomax.application.service.OdontogramaService;
import com.odontomax.application.service.PagoService;
import com.odontomax.tratamientos.service.TratamientoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpedienteLegacyAdapterTest {

    @Mock
    private TratamientoService tratamientoService;

    @Mock
    private PagoService pagoService;

    @Mock
    private NotaEvolucionService notaEvolucionService;

    @Mock
    private OdontogramaService odontogramaService;

    @InjectMocks
    private ExpedienteLegacyAdapter adapter;

    @Test
    @DisplayName("ExpedienteLegacyAdapter delega correctamente a los servicios legacy")
    void delegaServicios() {
        UUID id = UUID.randomUUID();

        when(tratamientoService.listarPorPaciente(id)).thenReturn(List.of(new TratamientoResponseDTO()));
        when(pagoService.listarPorPaciente(id)).thenReturn(List.of(new PagoResponseDTO()));
        when(notaEvolucionService.listarPorPaciente(id)).thenReturn(List.of(new NotaEvolucionResponseDTO()));
        when(odontogramaService.obtenerEstadoActual(id)).thenReturn(List.of(new OdontogramaRegistroResponseDTO()));

        assertEquals(1, adapter.listarTratamientosPorPaciente(id).size());
        assertEquals(1, adapter.listarPagosPorPaciente(id).size());
        assertEquals(1, adapter.listarNotasPorPaciente(id).size());
        assertEquals(1, adapter.obtenerOdontogramaPorPaciente(id).size());

        verify(tratamientoService).listarPorPaciente(id);
        verify(pagoService).listarPorPaciente(id);
        verify(notaEvolucionService).listarPorPaciente(id);
        verify(odontogramaService).obtenerEstadoActual(id);
    }
}