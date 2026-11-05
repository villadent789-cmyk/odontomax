package com.odontomax.pacientes.infrastructure.adapter;

import com.odontomax.agenda.api.ConsultaCitasModuloApi;
import com.odontomax.agenda.api.ConsultaEspecialistasModuloApi;
import com.odontomax.application.dto.response.CitaResponseDTO;
import com.odontomax.pacientes.infrastructure.persistence.repository.SpringDataPacienteRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AgendaCitasAdapterTest {

    private final ConsultaCitasModuloApi citas = mock(ConsultaCitasModuloApi.class);
    private final ConsultaEspecialistasModuloApi especialistas = mock(ConsultaEspecialistasModuloApi.class);
    private final SpringDataPacienteRepository pacientes = mock(SpringDataPacienteRepository.class);
    private final AgendaCitasAdapter adapter = new AgendaCitasAdapter(citas, especialistas, pacientes);

    @Test
    @DisplayName("Última atención: agrupa por paciente en una sola consulta y omite la consulta sin pacientes")
    void testUltimasAtenciones() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        LocalDate fecha = LocalDate.of(2026, 6, 15);
        when(citas.findUltimaAtencionPorPaciente(List.of(p1, p2))).thenReturn(Map.of(p1, fecha));

        Map<UUID, LocalDate> resultado = adapter.ultimasAtenciones(List.of(p1, p2));

        assertEquals(fecha, resultado.get(p1));
        assertNull(resultado.get(p2), "Paciente nunca atendido: sin fecha");
        verify(citas, times(1)).findUltimaAtencionPorPaciente(any());

        assertTrue(adapter.ultimasAtenciones(List.of()).isEmpty());
        assertTrue(adapter.ultimasAtenciones(null).isEmpty());
        verifyNoMoreInteractions(citas);
    }

    @Test
    @DisplayName("citasDePaciente: devuelve la lista de citas del paciente ordenadas por fecha")
    void testCitasDePaciente() {
        UUID pacienteId = UUID.randomUUID();
        when(citas.findByPacienteIdOrderByFechaDesc(pacienteId)).thenReturn(List.of(new CitaResponseDTO()));

        List<CitaResponseDTO> res = adapter.citasDePaciente(pacienteId);

        assertEquals(1, res.size());
        verify(citas).findByPacienteIdOrderByFechaDesc(pacienteId);
        assertTrue(adapter.citasDePaciente(null).isEmpty());
    }
}