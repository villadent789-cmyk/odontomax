package com.odontomax.agenda.application.service;

import com.odontomax.agenda.application.dto.request.CitaCancelarRequestDTO;
import com.odontomax.agenda.application.dto.request.CitaReprogramarRequestDTO;
import com.odontomax.agenda.application.dto.request.CitaRequestDTO;
import com.odontomax.agenda.application.dto.response.CitaResponseDTO;
import com.odontomax.agenda.application.port.out.CitaRepositoryPort;
import com.odontomax.agenda.application.port.out.DoctorHorarioConsultaPort;
import com.odontomax.agenda.application.port.out.PacienteReservaPort;
import com.odontomax.agenda.application.port.out.PagoAdelantoPort;
import com.odontomax.agenda.application.port.out.RegistroAuditoriaPort;
import com.odontomax.agenda.application.port.out.SaldoFavorPort;
import com.odontomax.agenda.application.port.out.SedeValidacionPort;
import com.odontomax.agenda.application.port.out.EspecialistaRepositoryPort;
import com.odontomax.agenda.application.port.out.TratamientoCitaPort;
import com.odontomax.agenda.domain.exception.CitaColisionException;
import com.odontomax.agenda.domain.exception.SillonEspecialistaFijoException;
import com.odontomax.agenda.domain.exception.SillonNoAsignadoException;
import com.odontomax.agenda.domain.model.Cita;
import com.odontomax.agenda.domain.model.Especialista;
import com.odontomax.agenda.domain.model.SedeAgenda;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import com.odontomax.identidad.api.ClinicaUserPrincipal;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CitaApplicationServiceTest {

    @Mock
    private CitaRepositoryPort citaRepository;

    @Mock
    private DoctorHorarioConsultaPort doctorHorarioPort;

    @Mock
    private RegistroAuditoriaPort auditoriaPort;

    @Mock
    private SedeValidacionPort sedeValidacionPort;

    @Mock
    private PagoAdelantoPort pagoAdelantoPort;

    @Mock
    private SaldoFavorPort saldoFavorPort;

    @Mock
    private PacienteReservaPort pacienteReservaPort;

    @Mock
    private EspecialistaRepositoryPort especialistaRepository;

    @Mock
    private com.odontomax.agenda.application.port.out.CitaContactoPort citaContactoPort;

    @Mock
    private TratamientoCitaPort tratamientoCitaPort;

    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private CitaApplicationService citaService;

    private UUID citaId;
    private UUID doctorId;
    private Cita citaExistente;

    @BeforeEach
    void setUp() {
        citaId = UUID.randomUUID();
        doctorId = UUID.randomUUID();
        lenient().when(especialistaRepository.findById(doctorId))
                .thenReturn(Optional.of(especialistaConSillon(1)));

        citaExistente = Cita.builder()
                .id(citaId)
                .pacienteId(UUID.randomUUID())
                .pacienteNombre("María Flores")
                .doctorId(doctorId)
                .doctorNombre("Dr. Alex Ríos")
                .sedeId("sede-chilca-1")
                .fecha(LocalDate.of(2026, 10, 5))
                .hora(LocalTime.of(10, 0))
                .duracionMinutos(30)
                .estado("PROGRAMADA")
                .build();

        ClinicaUserPrincipal principal = new ClinicaUserPrincipal(
                UUID.randomUUID(), "admin@odontomax.com", "Admin", "ADMIN_GENERAL", "sede-chilca-1"
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN_GENERAL")))
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Agendar: usa el sillón fijo del doctor cuando no se indica uno")
    void agendarCita_usaSillonFijoDelDoctor() {
        LocalDate fecha = LocalDate.of(2026, 10, 26);
        UUID pacienteId = UUID.randomUUID();
        prepararCitaConDoctor(fecha, pacienteId, especialistaConSillon(2));
        var request = solicitudCita(pacienteId, fecha, null);

        CitaResponseDTO resultado = citaService.agendarCita(request, "sede-chilca-1");

        assertEquals(2, resultado.getSillonNumero());
    }

    @Test
    @DisplayName("Agendar: rechaza un sillón solicitado distinto al fijo del doctor")
    void agendarCita_rechazaSillonDistintoAlFijo() {
        LocalDate fecha = LocalDate.of(2026, 10, 26);
        UUID pacienteId = UUID.randomUUID();
        when(doctorHorarioPort.obtenerNombreDoctor(doctorId)).thenReturn("Dra. Test");
        when(especialistaRepository.findById(doctorId)).thenReturn(Optional.of(especialistaConSillon(2)));
        var request = solicitudCita(pacienteId, fecha, 1);

        assertThrows(SillonEspecialistaFijoException.class, () ->
                citaService.agendarCita(request, "sede-chilca-1"));
        verify(citaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Agendar: rechaza la cita si el doctor no tiene sillón fijo en la sede")
    void agendarCita_sinSillonFijoRechaza() {
        LocalDate fecha = LocalDate.of(2026, 10, 26);
        UUID pacienteId = UUID.randomUUID();
        when(doctorHorarioPort.obtenerNombreDoctor(doctorId)).thenReturn("Dra. Test");
        when(especialistaRepository.findById(doctorId)).thenReturn(Optional.of(Especialista.builder()
                .id(doctorId).sedes(List.of("sede-chilca-1")).build()));
        var request = solicitudCita(pacienteId, fecha, 3);

        SillonNoAsignadoException error = assertThrows(SillonNoAsignadoException.class,
                () -> citaService.agendarCita(request, "sede-chilca-1"));

        assertEquals("El doctor no tiene sillón asignado en esta sede; asígnalo en Administración", error.getMessage());
        verify(citaRepository, never()).save(any());
    }

    private void prepararCitaConDoctor(LocalDate fecha, UUID pacienteId, Especialista especialista) {
        when(sedeValidacionPort.obtenerSede("sede-chilca-1"))
                .thenReturn(Optional.of(new SedeAgenda("sede-chilca-1", true, 4)));
        when(especialistaRepository.findById(doctorId)).thenReturn(Optional.of(especialista));
        when(pacienteReservaPort.findById(pacienteId)).thenReturn(Optional.of(
                new PacienteReservaPort.PacienteReservaInfo(pacienteId, "12345678", "Paciente Test", "999888777")));
        when(doctorHorarioPort.obtenerNombreDoctor(doctorId)).thenReturn("Dra. Test");
        when(doctorHorarioPort.esDoctorActivo(doctorId)).thenReturn(true);
        when(doctorHorarioPort.atiendeEnHorarioYSede(doctorId, "sede-chilca-1", fecha, LocalTime.of(9, 0), 30))
                .thenReturn(true);
        when(citaRepository.findOcupantesDoctor(doctorId, fecha)).thenReturn(List.of());
        when(citaRepository.findOcupantesSillon(eq("sede-chilca-1"), anyInt(), eq(fecha))).thenReturn(List.of());
        when(citaRepository.findByFechaAndSede(fecha, "sede-chilca-1")).thenReturn(List.of());
        when(citaRepository.save(any(Cita.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private CitaRequestDTO solicitudCita(UUID pacienteId, LocalDate fecha, Integer sillonNumero) {
        return CitaRequestDTO.builder()
                .pacienteId(pacienteId)
                .doctorId(doctorId)
                .fecha(fecha)
                .hora(LocalTime.of(9, 0))
                .duracionMinutos(30)
                .sillonNumero(sillonNumero)
                .motivoConsulta("Consulta general")
                .build();
    }

    private Especialista especialistaConSillon(int sillon) {
        return Especialista.builder()
                .id(doctorId)
                .sedes(List.of("sede-chilca-1"))
                .sillonesPorSede(Map.of("sede-chilca-1", sillon))
                .build();
    }

    @Test
    @DisplayName("Reprogramar: Caso feliz actualiza fecha, hora, motivo y guarda auditoría")
    void testReprogramarExitoso() {
        LocalDate nuevaFecha = LocalDate.of(2026, 10, 7);
        LocalTime nuevoHorario = LocalTime.of(11, 0);
        String motivo = "Paciente solicita cambio por motivos laborales";

        when(citaRepository.findByIdParaActualizar(citaId)).thenReturn(Optional.of(citaExistente));
        when(doctorHorarioPort.esDoctorActivo(doctorId)).thenReturn(true);
        when(doctorHorarioPort.atiendeEnHorarioYSede(doctorId, "sede-chilca-1", nuevaFecha, nuevoHorario, 30))
                .thenReturn(true);
        when(citaRepository.findOcupantesDoctor(doctorId, nuevaFecha)).thenReturn(List.of());
        when(citaRepository.save(any(Cita.class))).thenAnswer(inv -> inv.getArgument(0));

        CitaReprogramarRequestDTO dto = CitaReprogramarRequestDTO.builder()
                .nuevaFecha(nuevaFecha)
                .nuevoHorario(nuevoHorario)
                .motivo(motivo)
                .build();

        CitaResponseDTO reprogramada = citaService.reprogramarCita(citaId, dto);

        assertNotNull(reprogramada);
        assertEquals(nuevaFecha, reprogramada.getFecha());
        assertEquals(nuevoHorario, reprogramada.getHora());
        assertEquals(motivo, reprogramada.getMotivoReprogramacion());
        assertTrue(reprogramada.getNotas().contains("[REPROGRAMACIÓN"));

        verify(auditoriaPort, times(1)).registrar(eq("REPROGRAMAR_CITA"), eq("CITAS"), eq("sede-chilca-1"), any());
    }

    @Test
    @DisplayName("Reprogramar: reasigna el sillón fijo del especialista")
    void reprogramarCita_usaSillonFijoDelDoctor() {
        LocalDate nuevaFecha = LocalDate.of(2026, 10, 7);
        citaExistente.setSillonNumero(1);
        when(citaRepository.findByIdParaActualizar(citaId)).thenReturn(Optional.of(citaExistente));
        when(especialistaRepository.findById(doctorId)).thenReturn(Optional.of(especialistaConSillon(2)));
        when(doctorHorarioPort.esDoctorActivo(doctorId)).thenReturn(true);
        when(doctorHorarioPort.atiendeEnHorarioYSede(doctorId, "sede-chilca-1", nuevaFecha, LocalTime.of(11, 0), 30))
                .thenReturn(true);
        when(citaRepository.findOcupantesDoctor(doctorId, nuevaFecha)).thenReturn(List.of());
        when(sedeValidacionPort.obtenerSede("sede-chilca-1"))
                .thenReturn(Optional.of(new SedeAgenda("sede-chilca-1", true, 4)));
        when(citaRepository.findByFechaAndSede(nuevaFecha, "sede-chilca-1")).thenReturn(List.of());
        when(citaRepository.findOcupantesSillon("sede-chilca-1", 2, nuevaFecha)).thenReturn(List.of());
        when(citaRepository.save(any(Cita.class))).thenAnswer(inv -> inv.getArgument(0));

        var dto = CitaReprogramarRequestDTO.builder()
                .nuevaFecha(nuevaFecha)
                .nuevoHorario(LocalTime.of(11, 0))
                .motivo("Paciente solicita cambio")
                .build();

        assertEquals(2, citaService.reprogramarCita(citaId, dto).getSillonNumero());
    }

    @Test
    @DisplayName("Reprogramar: Lanza 409 CitaColisionException si el doctor ya está ocupado en el nuevo horario")
    void testReprogramarColisionDoctor() {
        LocalDate nuevaFecha = LocalDate.of(2026, 10, 7);
        LocalTime nuevoHorario = LocalTime.of(11, 0);
        String motivo = "Paciente solicita cambio de turno urgente";

        when(citaRepository.findByIdParaActualizar(citaId)).thenReturn(Optional.of(citaExistente));
        when(doctorHorarioPort.esDoctorActivo(doctorId)).thenReturn(true);
        when(doctorHorarioPort.atiendeEnHorarioYSede(doctorId, "sede-chilca-1", nuevaFecha, nuevoHorario, 30))
                .thenReturn(true);
        when(citaRepository.findOcupantesDoctor(doctorId, nuevaFecha)).thenReturn(List.of(Cita.builder()
                .id(UUID.randomUUID()).doctorId(doctorId).fecha(nuevaFecha).hora(LocalTime.of(10, 45))
                .duracionMinutos(30).estado("PROGRAMADA").build()));

        CitaReprogramarRequestDTO dto = CitaReprogramarRequestDTO.builder()
                .nuevaFecha(nuevaFecha)
                .nuevoHorario(nuevoHorario)
                .motivo(motivo)
                .build();

        CitaColisionException ex = assertThrows(CitaColisionException.class, () ->
                citaService.reprogramarCita(citaId, dto));

        assertTrue(ex.getMessage().contains("El doctor ya tiene una cita agendada"));
    }

    @Test
    @DisplayName("Reprogramar: Lanza 409 CitaColisionException si el especialista no atiende ese día o en esa sede")
    void testReprogramarDoctorFueraDeHorario() {
        LocalDate nuevaFecha = LocalDate.of(2026, 10, 8);
        LocalTime nuevoHorario = LocalTime.of(15, 0);
        String motivo = "Paciente solicita cambio de turno";

        when(citaRepository.findByIdParaActualizar(citaId)).thenReturn(Optional.of(citaExistente));
        when(doctorHorarioPort.esDoctorActivo(doctorId)).thenReturn(true);
        when(doctorHorarioPort.atiendeEnHorarioYSede(doctorId, "sede-chilca-1", nuevaFecha, nuevoHorario, 30))
                .thenReturn(false);

        CitaReprogramarRequestDTO dto = CitaReprogramarRequestDTO.builder()
                .nuevaFecha(nuevaFecha)
                .nuevoHorario(nuevoHorario)
                .motivo(motivo)
                .build();

        CitaColisionException ex = assertThrows(CitaColisionException.class, () ->
                citaService.reprogramarCita(citaId, dto));

        assertTrue(ex.getMessage().contains("El especialista no atiende en el horario o sede seleccionada"));
    }

    @Test
    @DisplayName("Cancelar: Actualiza estado a CANCELADA, motivo y genera auditoría")
    void testCancelarExitoso() {
        String motivo = "Paciente avisa que viaja por fuerza mayor";
        when(citaRepository.findByIdParaActualizar(citaId)).thenReturn(Optional.of(citaExistente));
        when(citaRepository.save(any(Cita.class))).thenAnswer(inv -> inv.getArgument(0));

        CitaCancelarRequestDTO dto = CitaCancelarRequestDTO.builder().motivo(motivo).build();
        CitaResponseDTO cancelada = citaService.cancelarCita(citaId, dto);

        assertNotNull(cancelada);
        assertEquals("CANCELADA", cancelada.getEstado());
        assertEquals(motivo, cancelada.getMotivoCancelacion());
        assertTrue(cancelada.getNotas().contains("[CANCELADA"));

        verify(auditoriaPort, times(1)).registrar(eq("CANCELAR_CITA"), eq("CITAS"), eq("sede-chilca-1"), any());
    }

    @Test
    @DisplayName("Cancelar: sin motivo (o muy corto) se rechaza y no se guarda nada")
    void testCancelarSinMotivo() {
        assertThrows(IllegalArgumentException.class, () ->
                citaService.cancelarCita(citaId, CitaCancelarRequestDTO.builder().motivo(null).build()));
        assertThrows(IllegalArgumentException.class, () ->
                citaService.cancelarCita(citaId, CitaCancelarRequestDTO.builder().motivo("  ").build()));
        assertThrows(IllegalArgumentException.class, () ->
                citaService.cancelarCita(citaId, CitaCancelarRequestDTO.builder().motivo("no").build()));
        verify(citaRepository, never()).save(any());
        verify(auditoriaPort, never()).registrar(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Cambiar estado a CANCELADA exige motivo y pasa por el flujo de cancelación auditado")
    void testCambiarEstadoACanceladaExigeMotivo() {
        assertThrows(IllegalArgumentException.class, () -> citaService.cambiarEstado(citaId, "CANCELADA", ""));
        verify(citaRepository, never()).save(any());

        when(citaRepository.findByIdParaActualizar(citaId)).thenReturn(Optional.of(citaExistente));
        when(citaRepository.save(any(Cita.class))).thenAnswer(inv -> inv.getArgument(0));
        CitaResponseDTO cancelada = citaService.cambiarEstado(citaId, "CANCELADA", "Paciente no se presentó");

        assertEquals("CANCELADA", cancelada.getEstado());
        assertEquals("Paciente no se presentó", cancelada.getMotivoCancelacion());
        verify(auditoriaPort, times(1)).registrar(eq("CANCELAR_CITA"), eq("CITAS"), eq("sede-chilca-1"), any());
    }

    @Test
    @DisplayName("Cancelar: una cita ya atendida no se puede cancelar")
    void testCancelarCitaAtendida() {
        citaExistente.setEstado("ATENDIDA");
        when(citaRepository.findByIdParaActualizar(citaId)).thenReturn(Optional.of(citaExistente));

        assertThrows(IllegalArgumentException.class, () ->
                citaService.cancelarCita(citaId, CitaCancelarRequestDTO.builder().motivo("Paciente pidió cancelar").build()));
        verify(citaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Lista de espera: retorna solo citas del día con estado EN_SALA")
    void testObtenerListaEspera() {
        Cita citaEnSala = Cita.builder()
                .id(UUID.randomUUID())
                .sedeId("sede-chilca-1")
                .fecha(LocalDate.now(CitaApplicationService.ZONE_LIMA))
                .hora(LocalTime.of(10, 0))
                .estado("EN_SALA")
                .build();

        Cita citaProgramada = Cita.builder()
                .id(UUID.randomUUID())
                .sedeId("sede-chilca-1")
                .fecha(LocalDate.now(CitaApplicationService.ZONE_LIMA))
                .hora(LocalTime.of(11, 0))
                .estado("PROGRAMADA")
                .build();

        when(citaRepository.findByFechaAndSede(eq(LocalDate.now(CitaApplicationService.ZONE_LIMA)), eq("sede-chilca-1")))
                .thenReturn(List.of(citaEnSala, citaProgramada));

        List<CitaResponseDTO> enEspera = citaService.obtenerListaEspera("sede-chilca-1");

        assertEquals(1, enEspera.size());
        assertEquals("EN_SALA", enEspera.get(0).getEstado());
        assertEquals(citaEnSala.getId(), enEspera.get(0).getId());
    }

    @Test
    @DisplayName("Reprogramar: un especialista inactivo no recibe nuevas citas (409)")
    void testReprogramarConEspecialistaInactivo() {
        when(citaRepository.findByIdParaActualizar(citaId)).thenReturn(Optional.of(citaExistente));
        when(doctorHorarioPort.esDoctorActivo(doctorId)).thenReturn(false);
        when(doctorHorarioPort.obtenerNombreDoctor(doctorId)).thenReturn("Dr. Inactivo");

        CitaReprogramarRequestDTO dto = CitaReprogramarRequestDTO.builder()
                .nuevaFecha(LocalDate.of(2026, 10, 7))
                .nuevoHorario(LocalTime.of(11, 0))
                .motivo("Paciente solicita cambio de turno")
                .build();

        assertThrows(CitaColisionException.class, () ->
                citaService.reprogramarCita(citaId, dto));
        verify(citaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Agenda por rango: consulta el repositorio entre desde y hasta (incluye citas futuras)")
    void obtenerCitasPorRango_DelegaAlRepositorio() {
        LocalDate desde = LocalDate.of(2026, 10, 1);
        LocalDate hasta = LocalDate.of(2026, 12, 31);
        Cita futura = Cita.builder().id(UUID.randomUUID()).fecha(LocalDate.of(2026, 10, 20)).build();
        when(citaRepository.findByRangoFechas(desde, hasta, "sede-chilca-1")).thenReturn(List.of(futura));

        List<CitaResponseDTO> resultado = citaService.obtenerCitasPorRango(desde, hasta, "sede-chilca-1");
        assertEquals(1, resultado.size());
        assertEquals(futura.getId(), resultado.get(0).getId());
    }

    @Test
    @DisplayName("Agenda por rango: rechaza rangos incompletos, invertidos o mayores a 400 días")
    void obtenerCitasPorRango_Validaciones() {
        LocalDate hoy = LocalDate.of(2026, 10, 3);
        assertThrows(IllegalArgumentException.class, () -> citaService.obtenerCitasPorRango(hoy, null, null));
        assertThrows(IllegalArgumentException.class, () -> citaService.obtenerCitasPorRango(hoy, hoy.minusDays(1), null));
        assertThrows(IllegalArgumentException.class, () -> citaService.obtenerCitasPorRango(hoy, hoy.plusDays(401), null));
        verify(citaRepository, never()).findByRangoFechas(any(), any(), any());
    }

    @Test
    @DisplayName("V2 Adelanto: registrar adelanto Yape confirma la cita y publica eventos")
    void testRegistrarAdelantoYape() {
        citaExistente.setEstado("PENDIENTE_DE_PAGO");
        citaExistente.setAdelantoRequerido(true);
        citaExistente.setAdelantoEstado("PENDIENTE");
        UUID planId = UUID.randomUUID();

        when(citaRepository.findByIdParaActualizar(citaId)).thenReturn(Optional.of(citaExistente));
        when(citaRepository.save(any(Cita.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tratamientoCitaPort.crearPlanParaAdelanto(
                eq(citaExistente.getPacienteId()), eq(citaExistente.getSedeId()), eq(doctorId),
                eq(citaExistente.getMotivoConsulta()), eq(new java.math.BigDecimal("30.00"))))
                .thenReturn(planId);

        var request = com.odontomax.agenda.application.dto.request.RegistrarAdelantoRequestDTO.builder()
                .medioPago("YAPE")
                .monto(new java.math.BigDecimal("30.00"))
                .numeroOperacion("9872145")
                .build();

        CitaResponseDTO resultado = citaService.registrarAdelanto(citaId, request);

        assertEquals("CONFIRMADA_CON_ADELANTO", resultado.getEstado());
        assertEquals("PAGADO", resultado.getAdelantoEstado());
        assertEquals("YAPE", resultado.getAdelantoMedioPago());
        assertEquals(new java.math.BigDecimal("30.00"), resultado.getAdelantoMonto());
        assertEquals(planId, resultado.getTratamientoId());
        verify(pagoAdelantoPort).registrarAdelanto(
                eq(citaId), any(), eq(planId), any(), eq(new java.math.BigDecimal("30.00")), eq("YAPE"), eq("9872145"), any());
        verify(eventPublisher).publishEvent(any(com.odontomax.agenda.domain.event.AdelantoRegistradoEvent.class));
        verify(eventPublisher).publishEvent(any(com.odontomax.agenda.domain.event.CitaConfirmadaEvent.class));
    }

    @Test
    @DisplayName("V2 Contacto: registrar contacto con resultado CONFIRMO confirma la cita")
    void testRegistrarContactoConfirmo() {
        when(citaRepository.findByIdParaActualizar(citaId)).thenReturn(Optional.of(citaExistente));
        when(citaRepository.save(any(Cita.class))).thenAnswer(inv -> inv.getArgument(0));

        var request = com.odontomax.agenda.application.dto.request.RegistrarContactoRequestDTO.builder()
                .medio("WHATSAPP")
                .resultado("CONFIRMO")
                .notas("Paciente confirmó por mensaje")
                .build();

        CitaResponseDTO resultado = citaService.registrarContacto(citaId, request);

        assertEquals("CONFIRMADA", resultado.getEstadoConfirmacion());
        assertEquals("WHATSAPP", resultado.getUltimoContactoMedio());
        verify(citaContactoPort).registrar(any(), any(), any(), any(), any());
        verify(eventPublisher).publishEvent(any(com.odontomax.agenda.domain.event.CitaConfirmadaEvent.class));
    }

    @Test
    @DisplayName("V2 Cancelación: con >= 24h de anticipación acredita saldo a favor del paciente")
    void testCancelarConAnticipacionOtorgaSaldoFavor() {
        citaExistente.setFecha(LocalDate.now(CitaApplicationService.ZONE_LIMA).plusDays(3));
        citaExistente.setAdelantoEstado("PAGADO");
        citaExistente.setAdelantoMonto(new java.math.BigDecimal("30.00"));

        when(citaRepository.findByIdParaActualizar(citaId)).thenReturn(Optional.of(citaExistente));
        when(sedeValidacionPort.obtenerConfiguracionSede("sede-chilca-1")).thenReturn(Optional.of(
                new SedeValidacionPort.SedeAgendaConfig("sede-chilca-1", "Villadent Chilca", "VILLADENT", true, "51964128340", "964128340",
                        new java.math.BigDecimal("30.00"), 2, 24, 24, LocalTime.of(8, 30), 7)
        ));
        when(citaRepository.save(any(Cita.class))).thenAnswer(inv -> inv.getArgument(0));

        var request = CitaCancelarRequestDTO.builder()
                .motivo("Viaje de emergencia cancela cita")
                .build();

        CitaResponseDTO resultado = citaService.cancelarCita(citaId, request);

        assertEquals("CANCELADA", resultado.getEstado());
        assertEquals("SALDO_FAVOR", resultado.getAdelantoEstado());
        verify(saldoFavorPort).acreditarSaldoFavor(eq(citaExistente.getPacienteId()), eq(citaId), eq(new java.math.BigDecimal("30.00")), any());
        verify(eventPublisher).publishEvent(any(com.odontomax.agenda.domain.event.CitaCanceladaEvent.class));
    }

    @Test
    @DisplayName("V2 Expiración: procesar citas vencidas de adelanto las marca como EXPIRADA")
    void testProcesarCitasExpiradas() {
        Cita vencida = Cita.builder()
                .id(UUID.randomUUID())
                .sedeId("sede-chilca-1")
                .estado("PENDIENTE_DE_PAGO")
                .adelantoEstado("PENDIENTE")
                .adelantoLimitePago(java.time.OffsetDateTime.now().minusMinutes(5))
                .build();

        when(citaRepository.findCitasVencidasDePago(any())).thenReturn(List.of(vencida));
        when(citaRepository.findByIdParaActualizar(vencida.getId())).thenReturn(Optional.of(vencida));

        int total = citaService.procesarCitasExpiradas();

        assertEquals(1, total);
        assertEquals("EXPIRADA", vencida.getEstado());
        assertEquals("PERDIDO", vencida.getAdelantoEstado());
        verify(citaRepository).save(vencida);
    }

    @Test
    @DisplayName("B-039 Expiracion: si al obtener el bloqueo la cita ya no esta PENDIENTE_DE_PAGO (se pago), no se expira ni se guarda")
    void testProcesarCitasExpiradas_OmiteLaQueSePagoMientras() {
        Cita enLista = Cita.builder().id(UUID.randomUUID()).sedeId("sede-chilca-1").estado("PENDIENTE_DE_PAGO")
                .adelantoEstado("PENDIENTE").build();
        Cita pagada = Cita.builder().id(enLista.getId()).sedeId("sede-chilca-1").estado("CONFIRMADA_CON_ADELANTO")
                .adelantoEstado("PAGADO").build();

        when(citaRepository.findCitasVencidasDePago(any())).thenReturn(List.of(enLista));
        when(citaRepository.findByIdParaActualizar(enLista.getId())).thenReturn(Optional.of(pagada));

        assertEquals(0, citaService.procesarCitasExpiradas());
        assertEquals("CONFIRMADA_CON_ADELANTO", pagada.getEstado());
        assertEquals("PAGADO", pagada.getAdelantoEstado());
        verify(citaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Agendar: Sede Chilca (4 sillones) rechaza cita si ya hay 4 citas solapadas en ese horario")
    void agendarCita_superaCapacidadSillonesSedeChilca_lanzaColisionException() {
        LocalDate fecha = LocalDate.of(2026, 10, 25);
        LocalTime hora = LocalTime.of(15, 0);

        when(sedeValidacionPort.obtenerSede("sede-chilca-1"))
                .thenReturn(Optional.of(new SedeAgenda("sede-chilca-1", true, 4)));

        List<Cita> cuatroOcupadas = List.of(
                Cita.builder().id(UUID.randomUUID()).fecha(fecha).hora(hora).duracionMinutos(30).estado("PROGRAMADA").build(),
                Cita.builder().id(UUID.randomUUID()).fecha(fecha).hora(hora).duracionMinutos(30).estado("CONFIRMADA").build(),
                Cita.builder().id(UUID.randomUUID()).fecha(fecha).hora(hora).duracionMinutos(30).estado("EN_SALA").build(),
                Cita.builder().id(UUID.randomUUID()).fecha(fecha).hora(hora).duracionMinutos(30).estado("PROGRAMADA").build()
        );
        when(citaRepository.findByFechaAndSede(fecha, "sede-chilca-1")).thenReturn(cuatroOcupadas);

        UUID pacienteId = UUID.randomUUID();
        when(pacienteReservaPort.findById(pacienteId)).thenReturn(Optional.of(
                new com.odontomax.agenda.application.port.out.PacienteReservaPort.PacienteReservaInfo(
                        pacienteId, "12345678", "Paciente Test", "999888777"
                )
        ));

        var request = com.odontomax.agenda.application.dto.request.CitaRequestDTO.builder()
                .pacienteId(pacienteId)
                .fecha(fecha)
                .hora(hora)
                .duracionMinutos(30)
                .motivoConsulta("Consulta general")
                .build();

        CitaColisionException ex = assertThrows(CitaColisionException.class, () ->
                citaService.agendarCita(request, "sede-chilca-1"));
        assertTrue(ex.getMessage().contains("límite máximo de capacidad (4 sillones)"));
    }

    @Test
    @DisplayName("Agendar: Sede Huancayo (2 sillones) rechaza cita si ya hay 2 citas solapadas en ese horario")
    void agendarCita_superaCapacidadSillonesSedeHuancayo_lanzaColisionException() {
        LocalDate fecha = LocalDate.of(2026, 10, 25);
        LocalTime hora = LocalTime.of(16, 0);

        when(sedeValidacionPort.obtenerSede("sede-eltambo"))
                .thenReturn(Optional.of(new SedeAgenda("sede-eltambo", true, 2)));

        List<Cita> dosOcupadas = List.of(
                Cita.builder().id(UUID.randomUUID()).fecha(fecha).hora(hora).duracionMinutos(30).estado("PROGRAMADA").build(),
                Cita.builder().id(UUID.randomUUID()).fecha(fecha).hora(hora).duracionMinutos(30).estado("CONFIRMADA").build()
        );
        when(citaRepository.findByFechaAndSede(fecha, "sede-eltambo")).thenReturn(dosOcupadas);

        UUID pacienteId = UUID.randomUUID();
        when(pacienteReservaPort.findById(pacienteId)).thenReturn(Optional.of(
                new com.odontomax.agenda.application.port.out.PacienteReservaPort.PacienteReservaInfo(
                        pacienteId, "12345678", "Paciente Test", "999888777"
                )
        ));

        var request = com.odontomax.agenda.application.dto.request.CitaRequestDTO.builder()
                .pacienteId(pacienteId)
                .fecha(fecha)
                .hora(hora)
                .duracionMinutos(30)
                .motivoConsulta("Endodoncia")
                .build();

        CitaColisionException ex = assertThrows(CitaColisionException.class, () ->
                citaService.agendarCita(request, "sede-eltambo"));
        assertTrue(ex.getMessage().contains("límite máximo de capacidad (2 sillones)"));
        verify(citaRepository).bloquearCapacidadDeSede(eq("sede-eltambo"), any());
    }

    @Test
    @DisplayName("Agendar: Registra adelanto inicial opcional al crear cita y queda CONFIRMADA")
    void agendarCita_conAdelantoInicial_registraPagoYQuedaConfirmada() {
        LocalDate fecha = LocalDate.of(2026, 10, 26);
        LocalTime hora = LocalTime.of(9, 0);
        UUID pacienteId = UUID.randomUUID();

        prepararCitaConDoctor(fecha, pacienteId, especialistaConSillon(1));
        UUID planId = UUID.randomUUID();
        when(tratamientoCitaPort.crearPlanParaAdelanto(
                pacienteId, "sede-chilca-1", doctorId, "Curación simple", new BigDecimal("50.00"))).thenReturn(planId);

        var request = com.odontomax.agenda.application.dto.request.CitaRequestDTO.builder()
                .pacienteId(pacienteId)
                .doctorId(doctorId)
                .fecha(fecha)
                .hora(hora)
                .duracionMinutos(30)
                .motivoConsulta("Curación simple")
                .montoAdelantoInicial(new BigDecimal("50.00"))
                .medioPagoAdelanto("YAPE")
                .numeroOperacionAdelanto("OP-998877")
                .build();

        CitaResponseDTO res = citaService.agendarCita(request, "sede-chilca-1");

        assertNotNull(res);
        assertEquals("CONFIRMADA", res.getEstado());
        assertEquals("CONFIRMADO", res.getAdelantoEstado());
        assertEquals(new BigDecimal("50.00"), res.getAdelantoMonto());
        assertEquals(planId, res.getTratamientoId());
        verify(pagoAdelantoPort, times(1)).registrarAdelanto(
                any(UUID.class), eq(pacienteId), eq(planId), eq("sede-chilca-1"),
                eq(new BigDecimal("50.00")), eq("YAPE"), eq("OP-998877"), any()
        );
    }

    @Test
    @DisplayName("Bandeja: crea recordatorios para los cumpleaños de mañana sin contarlos como pendientes de hoy")
    void obtenerBandejaTareas_incluyeCumpleanosMananaSinWhatsappNiContadoresDeHoy() {
        LocalDate hoy = LocalDate.of(2026, 10, 8);
        UUID pacienteId = UUID.randomUUID();
        var paciente = new PacienteReservaPort.PacienteCumpleaneroInfo(
                pacienteId, "12345678", "Ana Lima", "987654321", 2, true
        );
        when(sedeValidacionPort.obtenerConfiguracionSede("sede-chilca-1")).thenReturn(Optional.empty());
        when(citaRepository.findByFechaAndSede(hoy, "sede-chilca-1")).thenReturn(List.of());
        when(citaRepository.findByFechaAndSede(hoy.plusDays(1), "sede-chilca-1")).thenReturn(List.of());
        when(citaRepository.findCitasPendientesDePago("sede-chilca-1")).thenReturn(List.of());
        when(pacienteReservaPort.obtenerCumpleanerosDelDia("sede-chilca-1", 10, 8)).thenReturn(List.of());
        when(pacienteReservaPort.obtenerCumpleanerosDelDia("sede-chilca-1", 10, 9)).thenReturn(List.of(paciente));

        var bandeja = citaService.obtenerBandejaTareas("sede-chilca-1", hoy);

        var recordatorio = bandeja.getTareas().stream()
                .filter(t -> "RECORDATORIO_CUMPLEANOS".equals(t.getTipo()))
                .findFirst()
                .orElseThrow();
        assertEquals("CUMPLE-MANANA-" + pacienteId, recordatorio.getId());
        assertEquals("NORMAL", recordatorio.getPrioridad());
        assertEquals(pacienteId, recordatorio.getPacienteId());
        assertEquals("Ana Lima", recordatorio.getPacienteNombre());
        assertEquals("987654321", recordatorio.getPacienteTelefono());
        assertEquals("12345678", recordatorio.getPacienteDni());
        assertEquals(2, recordatorio.getTotalInasistencias());
        assertTrue(recordatorio.isAvisoInasistencias());
        assertNull(recordatorio.getMensajeSugerido());
        assertNull(recordatorio.getEnlaceWhatsapp());
        assertEquals(0, bandeja.getTotalPendientes());
        assertEquals(0, bandeja.getTotalCumpleanosPendientes());
        verify(pacienteReservaPort).obtenerCumpleanerosDelDia("sede-chilca-1", 10, 8);
        verify(pacienteReservaPort).obtenerCumpleanerosDelDia("sede-chilca-1", 10, 9);
    }

    @Test
    @DisplayName("Bandeja: no agrega tareas si no hay cumpleaños mañana")
    void obtenerBandejaTareas_sinCumpleanosMananaNoAgregaRecordatorios() {
        LocalDate hoy = LocalDate.of(2026, 10, 8);
        when(sedeValidacionPort.obtenerConfiguracionSede("sede-chilca-1")).thenReturn(Optional.empty());
        when(citaRepository.findByFechaAndSede(hoy, "sede-chilca-1")).thenReturn(List.of());
        when(citaRepository.findByFechaAndSede(hoy.plusDays(1), "sede-chilca-1")).thenReturn(List.of());
        when(citaRepository.findCitasPendientesDePago("sede-chilca-1")).thenReturn(List.of());
        when(pacienteReservaPort.obtenerCumpleanerosDelDia("sede-chilca-1", 10, 8)).thenReturn(List.of());
        when(pacienteReservaPort.obtenerCumpleanerosDelDia("sede-chilca-1", 10, 9)).thenReturn(List.of());

        var bandeja = citaService.obtenerBandejaTareas("sede-chilca-1", hoy);

        assertTrue(bandeja.getTareas().stream().noneMatch(t -> "RECORDATORIO_CUMPLEANOS".equals(t.getTipo())));
        assertEquals(0, bandeja.getTotalPendientes());
        verify(pacienteReservaPort).obtenerCumpleanerosDelDia("sede-chilca-1", 10, 9);
    }

    @Test
    @DisplayName("Bandeja: ofrece pasar al sillón y eleva prioridad solo después de 15 minutos")
    void obtenerBandejaTareas_priorizaEsperaMayorAQuinceMinutos() {
        LocalDate hoy = LocalDate.of(2026, 10, 8);
        Instant ahora = Instant.parse("2026-10-08T17:00:00Z");
        citaService.setClock(Clock.fixed(ahora, ZoneOffset.UTC));
        Cita esperaQuinceMinutos = citaEnSala("En quince", ahora.minusSeconds(15 * 60L));
        Cita esperaDieciseisMinutos = citaEnSala("En dieciséis", ahora.minusSeconds(16 * 60L));
        Cita llegadaDesconocida = citaEnSala("Llegada antigua", null);
        Cita yaIniciada = citaEnSala("Ya en sillón", ahora.minusSeconds(20 * 60L));
        yaIniciada.setInicioAtencionEn(ahora.minusSeconds(5 * 60L));

        when(sedeValidacionPort.obtenerConfiguracionSede("sede-chilca-1")).thenReturn(Optional.empty());
        when(citaRepository.findByFechaAndSede(hoy, "sede-chilca-1"))
                .thenReturn(List.of(esperaQuinceMinutos, esperaDieciseisMinutos, llegadaDesconocida, yaIniciada));
        when(citaRepository.findByFechaAndSede(hoy.plusDays(1), "sede-chilca-1")).thenReturn(List.of());
        when(citaRepository.findCitasPendientesDePago("sede-chilca-1")).thenReturn(List.of());
        when(pacienteReservaPort.obtenerCumpleanerosDelDia("sede-chilca-1", 10, 8)).thenReturn(List.of());
        when(pacienteReservaPort.obtenerCumpleanerosDelDia("sede-chilca-1", 10, 9)).thenReturn(List.of());

        var bandeja = citaService.obtenerBandejaTareas("sede-chilca-1", hoy);

        var tareasSillon = bandeja.getTareas().stream()
                .filter(t -> "PASAR_AL_SILLON".equals(t.getTipo()))
                .toList();
        assertEquals(3, tareasSillon.size());
        assertEquals(3, bandeja.getTotalPendientes());
        assertEquals("NORMAL", tareasSillon.stream()
                .filter(t -> "En quince".equals(t.getPacienteNombre())).findFirst().orElseThrow().getPrioridad());
        assertEquals("ALTA", tareasSillon.stream()
                .filter(t -> "En dieciséis".equals(t.getPacienteNombre())).findFirst().orElseThrow().getPrioridad());
        assertEquals("NORMAL", tareasSillon.stream()
                .filter(t -> "Llegada antigua".equals(t.getPacienteNombre())).findFirst().orElseThrow().getPrioridad());
    }

    @Test
    @DisplayName("Iniciar atención guarda la hora del servidor y registra auditoría")
    void iniciarAtencionGuardaMarcaYAuditoria() {
        Instant ahora = Instant.parse("2026-10-08T17:00:00Z");
        citaService.setClock(Clock.fixed(ahora, ZoneOffset.UTC));
        citaExistente.setEstado("EN_SALA");
        citaExistente.setLlegadaEn(ahora.minusSeconds(300));
        when(citaRepository.findByIdParaActualizar(citaId)).thenReturn(Optional.of(citaExistente));
        when(citaRepository.save(any(Cita.class))).thenAnswer(inv -> inv.getArgument(0));

        var actualizada = citaService.iniciarAtencion(citaId);

        assertEquals(ahora, actualizada.getInicioAtencionEn());
        verify(auditoriaPort).registrar(
                eq("INICIAR_ATENCION_CITA"), eq("CITAS"), eq("sede-chilca-1"), any());
    }

    private Cita citaEnSala(String nombre, Instant llegada) {
        return Cita.builder()
                .id(UUID.randomUUID())
                .pacienteNombre(nombre)
                .sedeId("sede-chilca-1")
                .fecha(LocalDate.of(2026, 10, 8))
                .hora(LocalTime.of(10, 0))
                .estado("EN_SALA")
                .llegadaEn(llegada)
                .build();
    }

}
