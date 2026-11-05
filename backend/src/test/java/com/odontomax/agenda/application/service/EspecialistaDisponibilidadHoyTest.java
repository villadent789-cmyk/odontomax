package com.odontomax.agenda.application.service;

import com.odontomax.agenda.application.dto.response.DisponibilidadResponseDTO;
import com.odontomax.agenda.application.dto.response.DisponibilidadSlotDTO;
import com.odontomax.agenda.application.port.out.CitaRepositoryPort;
import com.odontomax.agenda.application.port.out.EspecialistaHorarioRepositoryPort;
import com.odontomax.agenda.application.port.out.EspecialistaRepositoryPort;
import com.odontomax.agenda.application.port.out.RegistroAuditoriaPort;
import com.odontomax.agenda.application.port.out.SedeValidacionPort;
import com.odontomax.agenda.application.port.out.UsuarioConsultaPort;
import com.odontomax.agenda.application.port.out.VerificacionHistorialClinicoPort;
import com.odontomax.agenda.domain.model.Especialista;
import com.odontomax.agenda.domain.model.HorarioEspecialista;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/** B-036: la disponibilidad de HOY (hora de Lima) no ofrece franjas que ya pasaron. */
@ExtendWith(MockitoExtension.class)
class EspecialistaDisponibilidadHoyTest {

    @Mock private EspecialistaRepositoryPort especialistaRepository;
    @Mock private EspecialistaHorarioRepositoryPort especialistaHorarioRepository;
    @Mock private CitaRepositoryPort citaRepository;
    @Mock private VerificacionHistorialClinicoPort verificacionHistorialClinico;
    @Mock private UsuarioConsultaPort usuarioConsultaPort;
    @Mock private SedeValidacionPort sedeValidacionPort;
    @Mock private RegistroAuditoriaPort auditoriaPort;

    @InjectMocks
    private EspecialistaApplicationService service;

    private final UUID espId = UUID.randomUUID();
    private static final LocalDate LUNES = LocalDate.of(2026, 10, 5);

    @BeforeEach
    void setUp() {
        when(especialistaRepository.findById(espId)).thenReturn(Optional.of(
                Especialista.builder().id(espId).nombre("Dr. Alex Ríos").especialidad("Ortodoncia").activo(true).build()));
        when(especialistaHorarioRepository.findByEspecialistaIdAndSedeIdAndDiaSemanaAndActivoTrue(eq(espId), eq("sede-chilca-1"), eq(1)))
                .thenReturn(List.of(HorarioEspecialista.builder().id(UUID.randomUUID()).especialistaId(espId).sedeId("sede-chilca-1")
                        .diaSemana(1).horaInicio(LocalTime.of(9, 0)).horaFin(LocalTime.of(12, 0)).activo(true).build()));
    }

    /** Reloj fijo: 2026-10-05 a la hora de Lima indicada (Lima es UTC-5 todo el año). */
    private void ahoraEnLima(String hora) {
        Instant instante = LUNES.atTime(LocalTime.parse(hora)).atZone(CitaApplicationService.ZONE_LIMA).toInstant();
        service.setReloj(Clock.fixed(instante, CitaApplicationService.ZONE_LIMA));
    }

    private static DisponibilidadSlotDTO slot(DisponibilidadResponseDTO d, String hora) {
        return d.getSlots().stream().filter(s -> s.getHora().equals(LocalTime.parse(hora))).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("Hoy a las 10:10 (Lima): las franjas de 09:00, 09:30 y 10:00 ya pasaron; 10:30 en adelante siguen libres")
    void hoyNoOfreceFranjasPasadas() {
        ahoraEnLima("10:10");

        DisponibilidadResponseDTO d = service.obtenerDisponibilidad(espId, LUNES, "sede-chilca-1");

        assertEquals(6, d.getSlots().size());
        for (String pasada : List.of("09:00", "09:30", "10:00")) {
            assertFalse(slot(d, pasada).isDisponible(), pasada);
            assertEquals("PASADO", slot(d, pasada).getMotivoNoDisponible(), pasada);
        }
        for (String futura : List.of("10:30", "11:00", "11:30")) {
            assertTrue(slot(d, futura).isDisponible(), futura);
        }
    }

    @Test
    @DisplayName("Una franja que empieza justo ahora ya no se ofrece")
    void franjaQueEmpiezaAhora() {
        ahoraEnLima("10:30");

        DisponibilidadResponseDTO d = service.obtenerDisponibilidad(espId, LUNES, "sede-chilca-1");

        assertFalse(slot(d, "10:30").isDisponible());
        assertTrue(slot(d, "11:00").isDisponible());
    }

    @Test
    @DisplayName("Otras fechas no se filtran: la disponibilidad de una fecha futura conserva todas sus franjas")
    void otroDiaNoSeFiltra() {
        ahoraEnLima("10:10");

        DisponibilidadResponseDTO manana = service.obtenerDisponibilidad(espId, LUNES, "sede-chilca-1");
        assertTrue(slot(manana, "10:30").isDisponible());

        // Una fecha futura (el lunes siguiente) conserva todas sus franjas libres aunque hoy sea más tarde.
        ahoraEnLima("11:45");
        when(especialistaHorarioRepository.findByEspecialistaIdAndSedeIdAndDiaSemanaAndActivoTrue(eq(espId), eq("sede-chilca-1"), eq(1)))
                .thenReturn(List.of(HorarioEspecialista.builder().id(UUID.randomUUID()).especialistaId(espId).sedeId("sede-chilca-1")
                        .diaSemana(1).horaInicio(LocalTime.of(9, 0)).horaFin(LocalTime.of(10, 0)).activo(true).build()));
        DisponibilidadResponseDTO proximo = service.obtenerDisponibilidad(espId, LUNES.plusDays(7), "sede-chilca-1");
        assertTrue(proximo.getSlots().stream().allMatch(DisponibilidadSlotDTO::isDisponible));
    }
}
