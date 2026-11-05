package com.odontomax.agenda.application.service;

import com.odontomax.agenda.application.dto.request.ReservaPublicaRequestDTO;
import com.odontomax.agenda.application.dto.response.ConsultaCitaResponseDTO;
import com.odontomax.agenda.application.dto.response.TicketReservaDTO;
import com.odontomax.agenda.application.port.out.CitaRepositoryPort;
import com.odontomax.agenda.application.port.out.PacienteReservaPort;
import com.odontomax.agenda.application.port.out.SedeValidacionPort;
import com.odontomax.agenda.domain.model.Cita;
import com.odontomax.agenda.domain.model.Especialista;
import com.odontomax.agenda.application.port.out.EspecialistaRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservaPublicaApplicationServiceTest {

    @Mock
    private CitaRepositoryPort citaRepository;

    @Mock
    private PacienteReservaPort pacienteReservaPort;

    @Mock
    private SedeValidacionPort sedeValidacionPort;

    @InjectMocks
    private ReservaPublicaApplicationService service;

    private final UUID pacienteId = UUID.randomUUID();
    private final String dni = "44556677";
    private final LocalDate fechaFutura = LocalDate.now(ReservaPublicaApplicationService.ZONE_LIMA).plusDays(5);

    private ReservaPublicaRequestDTO buildRequest() {
        return ReservaPublicaRequestDTO.builder()
                .dni(dni)
                .nombres("Rosa")
                .apellidos("Flores")
                .telefono("987654321")
                .sedeId("sede-chilca-1")
                .fechaPreferencia(fechaFutura)
                .turno("MANANA")
                .motivoConsulta("Ortodoncia")
                .build();
    }

    @Test
    @DisplayName("Registrar reserva: rechaza fecha pasada en zona horaria America/Lima")
    void testRegistrarReservaFechaPasada() {
        ReservaPublicaRequestDTO dto = buildRequest();
        dto.setFechaPreferencia(LocalDate.now(ReservaPublicaApplicationService.ZONE_LIMA).minusDays(1));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                service.registrarReservaPublica(dto));
        assertTrue(ex.getMessage().contains("pasado"));
        verifyNoInteractions(citaRepository, pacienteReservaPort);
    }

    @Test
    @DisplayName("Registrar reserva: rechaza si se supera el límite de 10 reservas pendientes por turno")
    void testRegistrarReservaLimiteSuperado() {
        when(citaRepository.countPendientesWebPorTurno(eq("sede-chilca-1"), eq(fechaFutura), eq(true)))
                .thenReturn(10L);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                service.registrarReservaPublica(buildRequest()));
        assertTrue(ex.getMessage().contains("No hay disponibilidad"));
        verifyNoInteractions(pacienteReservaPort);
    }

    @Test
    @DisplayName("Registrar reserva: registra paciente, genera ticket y guarda cita en estado PENDIENTE_WEB")
    void testRegistrarReservaExitoso() {
        when(citaRepository.countPendientesWebPorTurno(eq("sede-chilca-1"), eq(fechaFutura), eq(true)))
                .thenReturn(3L);

        when(pacienteReservaPort.obtenerOCrearPaciente(dni, "Rosa", "Flores", "987654321", "sede-chilca-1"))
                .thenReturn(new PacienteReservaPort.PacienteReservaInfo(pacienteId, dni, "Rosa Flores", "987654321"));

        TicketReservaDTO ticket = service.registrarReservaPublica(buildRequest());

        assertNotNull(ticket);
        assertNotNull(ticket.getCodigoTicket());
        assertTrue(ticket.getCodigoTicket().startsWith("OM-"));
        assertEquals("PENDIENTE_WEB", ticket.getEstado());
        assertEquals("MANANA", ticket.getTurno());
        assertEquals("sede-chilca-1", ticket.getSedeId());

        ArgumentCaptor<Cita> citaCaptor = ArgumentCaptor.forClass(Cita.class);
        verify(citaRepository).save(citaCaptor.capture());
        Cita guardada = citaCaptor.getValue();
        assertEquals(pacienteId, guardada.getPacienteId());
        assertEquals("PENDIENTE_WEB", guardada.getEstado());
        assertEquals(ticket.getCodigoTicket(), guardada.getCodigoTicket());
        assertEquals(LocalTime.of(9, 30), guardada.getHora());
    }

    @Test
    @DisplayName("Consultar reserva: retorna datos de la cita si el ticket y DNI coinciden")
    void testConsultarReservaExitoso() {
        String codigoTicket = "OM-7K9M2P4X";
        Cita cita = Cita.builder()
                .id(UUID.randomUUID())
                .pacienteId(pacienteId)
                .codigoTicket(codigoTicket)
                .estado("PENDIENTE_WEB")
                .fecha(fechaFutura)
                .hora(LocalTime.of(9, 30))
                .sedeId("sede-chilca-1")
                .build();

        when(citaRepository.findByCodigoTicket(codigoTicket)).thenReturn(Optional.of(cita));
        when(pacienteReservaPort.findById(pacienteId))
                .thenReturn(Optional.of(new PacienteReservaPort.PacienteReservaInfo(pacienteId, dni, "Rosa Flores", "987654321")));

        ConsultaCitaResponseDTO response = service.consultarReservaPublica(codigoTicket, dni);

        assertNotNull(response);
        assertEquals(codigoTicket, response.getTicket());
        assertEquals("PENDIENTE_WEB", response.getEstado());
        assertEquals("MANANA", response.getTurno());
        assertEquals("sede-chilca-1", response.getSedeId());
    }

    @Test
    @DisplayName("Consultar reserva: rechaza si el DNI no coincide")
    void testConsultarReservaDniNoCoincide() {
        String codigoTicket = "OM-7K9M2P4X";
        Cita cita = Cita.builder()
                .id(UUID.randomUUID())
                .pacienteId(pacienteId)
                .codigoTicket(codigoTicket)
                .build();

        when(citaRepository.findByCodigoTicket(codigoTicket)).thenReturn(Optional.of(cita));
        when(pacienteReservaPort.findById(pacienteId))
                .thenReturn(Optional.of(new PacienteReservaPort.PacienteReservaInfo(pacienteId, dni, "Rosa Flores", "987654321")));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                service.consultarReservaPublica(codigoTicket, "99999999"));
        assertTrue(ex.getMessage().contains("No se encontró ninguna reserva activa"));
    }

    @Test
    @DisplayName("Consultar reserva: rechaza si el ticket no existe")
    void testConsultarReservaTicketNoExiste() {
        when(citaRepository.findByCodigoTicket("OM-NOEXISTE")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () ->
                service.consultarReservaPublica("OM-NOEXISTE", dni));
    }

    @Test
    @DisplayName("Registrar reserva: rechaza combinación incoherente de sede y servicio de distinto negocio")
    void testRegistrarReservaCombinacionIncoherente() {
        SedeValidacionPort.SedeAgendaConfig configSede = new SedeValidacionPort.SedeAgendaConfig(
                "sede-chilca-1", "Sede Chilca", "VILLADENT", true, null, null, null, 2, 24, 24, null, 30
        );
        when(sedeValidacionPort.obtenerConfiguracionSede("sede-chilca-1")).thenReturn(Optional.of(configSede));
        when(sedeValidacionPort.obtenerUnidadDeNegocioDelServicio("vmx-panoramica")).thenReturn(Optional.of("VILLAMAX"));

        ReservaPublicaRequestDTO dto = buildRequest();
        dto.setMotivoConsulta("vmx-panoramica");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                service.registrarReservaPublica(dto));
        assertTrue(ex.getMessage().contains("Combinación incoherente"));
    }

    @Test
    @DisplayName("Registrar reserva: acepta combinación coherente de sede y servicio del mismo negocio")
    void testRegistrarReservaCombinacionCoherente() {
        SedeValidacionPort.SedeAgendaConfig configSede = new SedeValidacionPort.SedeAgendaConfig(
                "sede-chilca-1", "Sede Chilca", "VILLADENT", true, null, null, null, 2, 24, 24, null, 30
        );
        when(sedeValidacionPort.obtenerConfiguracionSede("sede-chilca-1")).thenReturn(Optional.of(configSede));
        when(sedeValidacionPort.obtenerUnidadDeNegocioDelServicio("Ortodoncia")).thenReturn(Optional.of("VILLADENT"));
        when(citaRepository.countPendientesWebPorTurno(eq("sede-chilca-1"), eq(fechaFutura), eq(true)))
                .thenReturn(1L);
        when(pacienteReservaPort.obtenerOCrearPaciente(dni, "Rosa", "Flores", "987654321", "sede-chilca-1"))
                .thenReturn(new PacienteReservaPort.PacienteReservaInfo(pacienteId, dni, "Rosa Flores", "987654321"));

        TicketReservaDTO ticket = service.registrarReservaPublica(buildRequest());
        assertNotNull(ticket);
        assertEquals("PENDIENTE_WEB", ticket.getEstado());
    }

    @Test
    @DisplayName("DNI existente: usa el paciente existente, no expone datos privados y agenda con doctor")
    void testDniExistente_usaPacienteExistenteSinExponerDatos() {
        UUID docId = UUID.randomUUID();
        Especialista esp = new Especialista();
        esp.setId(docId);
        esp.setNombre("Doctor B");
        esp.setActivo(true);
        EspecialistaRepositoryPort espRepo = org.mockito.Mockito.mock(EspecialistaRepositoryPort.class);
        when(espRepo.findBySedeIdAndActivoTrue("sede-chilca-1")).thenReturn(java.util.List.of(esp));
        service.setEspecialistaRepository(espRepo);

        when(citaRepository.countPendientesWebPorTurno(eq("sede-chilca-1"), eq(fechaFutura), eq(true)))
                .thenReturn(1L);
        // Paciente existente en la sede/negocio
        when(pacienteReservaPort.existePorDniEnSede(dni, "sede-chilca-1")).thenReturn(true);
        // El nombre guardado es Carlos Real, el visitante escribió "Juan Falso"
        when(pacienteReservaPort.obtenerOCrearPaciente(dni, "Juan", "Falso", "987654321", "sede-chilca-1"))
                .thenReturn(new PacienteReservaPort.PacienteReservaInfo(pacienteId, dni, "Carlos Real", "999888777"));

        ReservaPublicaRequestDTO dto = buildRequest();
        dto.setNombres("Juan");
        dto.setApellidos("Falso");
        dto.setDoctorId(docId);
        dto.setPacienteNuevo(false);

        TicketReservaDTO ticket = service.registrarReservaPublica(dto);

        assertNotNull(ticket);
        assertEquals("PENDIENTE_WEB", ticket.getEstado());
        // El ticket NO expone datos privados del paciente (ni nombre ni teléfono ni DNI)
        assertFalse(ticket.getMensaje().contains("Carlos Real"));
        assertFalse(ticket.getMensaje().contains(dni));

        ArgumentCaptor<Cita> citaCaptor = ArgumentCaptor.forClass(Cita.class);
        verify(citaRepository).save(citaCaptor.capture());
        Cita guardada = citaCaptor.getValue();
        // La cita guarda el nombre REAL del paciente registrado, ignorando el nombre enviado
        assertEquals("Carlos Real", guardada.getPacienteNombre());
        assertEquals(docId, guardada.getDoctorId());
        assertEquals("Doctor B", guardada.getDoctorNombre());
    }

    @Test
    @DisplayName("Paciente nuevo: solicitud registrada como PENDIENTE_WEB para confirmación por WhatsApp")
    void testPacienteNuevo_creaSolicitudPendienteWeb() {
        when(citaRepository.countPendientesWebPorTurno(eq("sede-chilca-1"), eq(fechaFutura), eq(true)))
                .thenReturn(2L);
        // Marcó explícitamente "Soy paciente nuevo"
        when(pacienteReservaPort.obtenerOCrearPaciente(dni, "Rosa", "Flores", "987654321", "sede-chilca-1"))
                .thenReturn(new PacienteReservaPort.PacienteReservaInfo(pacienteId, dni, "Rosa Flores", "987654321"));

        ReservaPublicaRequestDTO dto = buildRequest();
        dto.setPacienteNuevo(true);

        TicketReservaDTO ticket = service.registrarReservaPublica(dto);

        assertNotNull(ticket);
        assertEquals("PENDIENTE_WEB", ticket.getEstado());
        assertTrue(ticket.getMensaje().contains("WhatsApp"));
        assertFalse(ticket.isAdelantoRequerido());

        ArgumentCaptor<Cita> citaCaptor = ArgumentCaptor.forClass(Cita.class);
        verify(citaRepository).save(citaCaptor.capture());
        Cita guardada = citaCaptor.getValue();
        assertEquals("PENDIENTE_WEB", guardada.getEstado());
    }

    @Test
    @DisplayName("Doctor que no atiende en la sede elegida: la reserva se rechaza")
    void testDoctorDeOtraSede_seRechaza() {
        UUID doctorAjeno = UUID.randomUUID();
        EspecialistaRepositoryPort espRepo = org.mockito.Mockito.mock(EspecialistaRepositoryPort.class);
        when(espRepo.findBySedeIdAndActivoTrue("sede-chilca-1")).thenReturn(java.util.List.of());
        service.setEspecialistaRepository(espRepo);
        when(citaRepository.countPendientesWebPorTurno(eq("sede-chilca-1"), eq(fechaFutura), eq(true)))
                .thenReturn(1L);
        when(pacienteReservaPort.existePorDniEnSede(dni, "sede-chilca-1")).thenReturn(true);
        when(pacienteReservaPort.obtenerOCrearPaciente(dni, "Rosa", "Flores", "987654321", "sede-chilca-1"))
                .thenReturn(new PacienteReservaPort.PacienteReservaInfo(UUID.randomUUID(), dni, "Rosa Flores", "987654321"));
        ReservaPublicaRequestDTO dto = buildRequest();
        dto.setDoctorId(doctorAjeno);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.registrarReservaPublica(dto));
        assertTrue(ex.getMessage().contains("no atiende en la sede"));
        verify(citaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Registrar reserva: un mismo paciente no puede tener más de 3 reservas web sin confirmar")
    void testRegistrarReservaTopePorPaciente() {
        when(citaRepository.countPendientesWebPorTurno(eq("sede-chilca-1"), eq(fechaFutura), eq(true))).thenReturn(0L);
        when(pacienteReservaPort.obtenerOCrearPaciente(dni, "Rosa", "Flores", "987654321", "sede-chilca-1"))
                .thenReturn(new PacienteReservaPort.PacienteReservaInfo(pacienteId, dni, "Rosa Flores", "987654321"));
        when(citaRepository.countReservasWebActivasDelPaciente(eq(pacienteId), any(LocalDate.class))).thenReturn(3L);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                service.registrarReservaPublica(buildRequest()));
        assertTrue(ex.getMessage().contains("3 reservas pendientes"));
        verify(citaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Registrar reserva: con 2 reservas web sin confirmar todavía permite la tercera")
    void testRegistrarReservaBajoElTopePorPaciente() {
        when(citaRepository.countPendientesWebPorTurno(eq("sede-chilca-1"), eq(fechaFutura), eq(true))).thenReturn(0L);
        when(pacienteReservaPort.obtenerOCrearPaciente(dni, "Rosa", "Flores", "987654321", "sede-chilca-1"))
                .thenReturn(new PacienteReservaPort.PacienteReservaInfo(pacienteId, dni, "Rosa Flores", "987654321"));
        when(citaRepository.countReservasWebActivasDelPaciente(eq(pacienteId), any(LocalDate.class))).thenReturn(2L);

        assertNotNull(service.registrarReservaPublica(buildRequest()));
        verify(citaRepository).save(any());
    }
}
