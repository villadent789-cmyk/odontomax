package com.odontomax.agenda.application.service;

import com.odontomax.agenda.application.dto.response.DisponibilidadResponseDTO;
import com.odontomax.agenda.application.port.out.CitaRepositoryPort;
import com.odontomax.agenda.application.port.out.EspecialistaHorarioRepositoryPort;
import com.odontomax.agenda.application.port.out.EspecialistaRepositoryPort;
import com.odontomax.agenda.application.port.out.RegistroAuditoriaPort;
import com.odontomax.agenda.application.port.out.SedeValidacionPort;
import com.odontomax.agenda.application.port.out.UsuarioConsultaPort;
import com.odontomax.agenda.application.port.out.VerificacionHistorialClinicoPort;
import com.odontomax.agenda.domain.model.Cita;
import com.odontomax.agenda.domain.model.Especialista;
import com.odontomax.agenda.domain.model.HorarioEspecialista;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EspecialistaApplicationServiceDisponibilidadTest {

    @Mock private EspecialistaRepositoryPort especialistaRepository;
    @Mock private EspecialistaHorarioRepositoryPort especialistaHorarioRepository;
    @Mock private CitaRepositoryPort citaRepository;
    @Mock private VerificacionHistorialClinicoPort verificacionHistorialClinico;
    @Mock private UsuarioConsultaPort usuarioConsultaPort;
    @Mock private SedeValidacionPort sedeValidacionPort;
    @Mock private RegistroAuditoriaPort auditoriaPort;

    @InjectMocks
    private EspecialistaApplicationService service;

    private UUID espId;
    private Especialista especialista;

    @BeforeEach
    void setUp() {
        espId = UUID.randomUUID();
        especialista = Especialista.builder()
                .id(espId)
                .nombre("Dr. Alex Ríos")
                .especialidad("Ortodoncia")
                .activo(true)
                .sedes(List.of("sede-chilca-1"))
                .build();
    }

    @Test
    @DisplayName("Disponibilidad: Retorna atiendeEseDia=false si no hay horarios configurados para ese día")
    void testDisponibilidadSinHorarios() {
        LocalDate fecha = LocalDate.of(2026, 10, 4); // Domingo
        when(especialistaRepository.findById(espId)).thenReturn(Optional.of(especialista));
        when(especialistaHorarioRepository.findByEspecialistaIdAndSedeIdAndDiaSemanaAndActivoTrue(eq(espId), eq("sede-chilca-1"), anyInt()))
                .thenReturn(List.of());

        DisponibilidadResponseDTO resp = service.obtenerDisponibilidad(espId, fecha, "sede-chilca-1");

        assertNotNull(resp);
        assertFalse(resp.isAtiendeEseDia());
        assertTrue(resp.getSlots().isEmpty());
    }

    @Test
    @DisplayName("Disponibilidad: Genera slots de 30 min y marca ocupado el turno de una cita existente")
    void testDisponibilidadConSlotsYCitaOcupada() {
        LocalDate fecha = LocalDate.of(2026, 10, 5); // Lunes (diaSemana=1)
        when(especialistaRepository.findById(espId)).thenReturn(Optional.of(especialista));

        HorarioEspecialista horario = HorarioEspecialista.builder()
                .id(UUID.randomUUID())
                .especialistaId(espId)
                .sedeId("sede-chilca-1")
                .diaSemana(1)
                .horaInicio(LocalTime.of(9, 0))
                .horaFin(LocalTime.of(11, 0)) // 9:00, 9:30, 10:00, 10:30 (4 slots)
                .activo(true)
                .build();

        when(especialistaHorarioRepository.findByEspecialistaIdAndSedeIdAndDiaSemanaAndActivoTrue(eq(espId), eq("sede-chilca-1"), eq(1)))
                .thenReturn(List.of(horario));

        // Cita existente a las 09:30 con duración 30 min
        Cita cita = Cita.builder()
                .id(UUID.randomUUID())
                .doctorId(espId)
                .fecha(fecha)
                .hora(LocalTime.of(9, 30))
                .duracionMinutos(30)
                .estado("PROGRAMADA")
                .build();

        when(citaRepository.findByDoctorIdAndFecha(espId, fecha)).thenReturn(List.of(cita));

        DisponibilidadResponseDTO resp = service.obtenerDisponibilidad(espId, fecha, "sede-chilca-1");

        assertNotNull(resp);
        assertTrue(resp.isAtiendeEseDia());
        assertEquals(4, resp.getSlots().size());

        // Slot 09:00 -> disponible
        assertTrue(resp.getSlots().get(0).isDisponible());
        assertEquals(LocalTime.of(9, 0), resp.getSlots().get(0).getHora());

        // Slot 09:30 -> OCUPADO
        assertFalse(resp.getSlots().get(1).isDisponible());
        assertEquals(LocalTime.of(9, 30), resp.getSlots().get(1).getHora());
        assertEquals("OCUPADO", resp.getSlots().get(1).getMotivoNoDisponible());

        // Slot 10:00 -> disponible
        assertTrue(resp.getSlots().get(2).isDisponible());

        // Slot 10:30 -> disponible
        assertTrue(resp.getSlots().get(3).isDisponible());
    }

    @Test
    @DisplayName("Disponibilidad: un especialista inactivo no ofrece turnos")
    void disponibilidad_inactivo() {
        when(especialistaRepository.findById(espId)).thenReturn(Optional.of(
                Especialista.builder().id(espId).nombre("X").especialidad("Y").activo(false).build()));

        var disp = service.obtenerDisponibilidad(espId, LocalDate.now().plusDays(1), "sede-chilca-1");

        assertFalse(disp.isAtiendeEseDia());
        assertTrue(disp.getSlots().isEmpty());
        verify(especialistaHorarioRepository, never()).findByEspecialistaIdAndSedeIdAndDiaSemanaAndActivoTrue(any(), anyString(), anyInt());
        verify(citaRepository, never()).findByDoctorIdAndFecha(any(), any());
        verifyNoInteractions(auditoriaPort);
        verify(especialistaRepository, never()).save(any());
        verify(especialistaRepository, never()).vincularSede(any(), eq("sede-chilca-1"));
    }
}
