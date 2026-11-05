package com.odontomax.agenda.application.service;

import com.odontomax.agenda.application.dto.request.CitaReprogramarRequestDTO;
import com.odontomax.agenda.application.dto.response.CitaResponseDTO;
import com.odontomax.agenda.application.port.out.CitaRepositoryPort;
import com.odontomax.agenda.application.port.out.DoctorHorarioConsultaPort;
import com.odontomax.agenda.application.port.out.RegistroAuditoriaPort;
import com.odontomax.agenda.domain.model.Cita;
import com.odontomax.identidad.api.ClinicaUserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Ciclo de vida de la cita: PENDIENTE_WEB/PROGRAMADA → CONFIRMADA → EN_SALA → ATENDIDA,
 * cancelación con motivo y transiciones bloqueadas según las reglas de negocio de CitaApplicationService.
 */
@ExtendWith(MockitoExtension.class)
class CitaTransicionesEstadoTest {

    @Mock
    private CitaRepositoryPort citaRepository;

    @Mock
    private DoctorHorarioConsultaPort doctorHorarioPort;

    @Mock
    private RegistroAuditoriaPort auditoriaPort;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private CitaApplicationService citaService;

    private final UUID citaId = UUID.randomUUID();
    private Cita cita;

    @BeforeEach
    void setUp() {
        cita = Cita.builder()
                .id(citaId)
                .pacienteId(UUID.randomUUID())
                .pacienteNombre("María Flores")
                .sedeId("sede-chilca-1")
                .fecha(LocalDate.of(2026, 10, 5))
                .hora(LocalTime.of(10, 0))
                .duracionMinutos(30)
                .estado("PROGRAMADA")
                .build();

        // El servicio exige un usuario autenticado con acceso a la sede de la cita.
        ClinicaUserPrincipal principal = new ClinicaUserPrincipal(
                UUID.randomUUID(), "admin@odontomax.com", "Admin", "ADMIN_GENERAL", "sede-chilca-1");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN_GENERAL"))));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void citaPersistida(String estado) {
        cita.setEstado(estado);
        when(citaRepository.findByIdParaActualizar(citaId)).thenReturn(Optional.of(cita));
    }

    private void saveDevuelveArgumento() {
        when(citaRepository.save(any(Cita.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @ParameterizedTest(name = "{0} → CONFIRMADA")
    @ValueSource(strings = {"PENDIENTE_WEB", "PROGRAMADA"})
    @DisplayName("Confirmar: PENDIENTE_WEB y PROGRAMADA pasan a CONFIRMADA sin tocar el motivo de cancelación")
    void confirmarDesdeEstadosIniciales(String estadoInicial) {
        citaPersistida(estadoInicial);
        saveDevuelveArgumento();

        CitaResponseDTO confirmada = citaService.cambiarEstado(citaId, "CONFIRMADA", null);

        assertNotNull(confirmada);
        assertEquals(citaId, confirmada.getId());
        assertEquals("CONFIRMADA", confirmada.getEstado());
        assertEquals("María Flores", confirmada.getPacienteNombre());
        assertNull(confirmada.getMotivoCancelacion());
        verify(citaRepository).save(cita);
    }

    @Test
    @DisplayName("Ciclo completo: CONFIRMADA → EN_SALA → ATENDIDA")
    void cicloCompletoDesdeConfirmada() {
        citaPersistida("CONFIRMADA");
        saveDevuelveArgumento();

        CitaResponseDTO enSala = citaService.cambiarEstado(citaId, "EN_SALA", null);
        assertNotNull(enSala);
        assertEquals("EN_SALA", enSala.getEstado());

        CitaResponseDTO enAtencion = citaService.cambiarEstado(citaId, "EN_ATENCION", null);
        assertNotNull(enAtencion);
        assertEquals("EN_ATENCION", enAtencion.getEstado());

        CitaResponseDTO atendida = citaService.cambiarEstado(citaId, "ATENDIDA", null);
        assertNotNull(atendida);
        assertEquals("ATENDIDA", atendida.getEstado());

        verify(citaRepository, times(3)).save(cita);
    }

    @Test
    @DisplayName("Confirmar queda auditado con usuario, sede y transición anterior → nueva")
    void confirmarRegistraAuditoria() {
        citaPersistida("PROGRAMADA");
        saveDevuelveArgumento();

        citaService.cambiarEstado(citaId, "CONFIRMADA", null);

        ArgumentCaptor<String> accionCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> moduloCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> sedeCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> detalleCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditoriaPort).registrar(accionCaptor.capture(), moduloCaptor.capture(), sedeCaptor.capture(), detalleCaptor.capture());
        assertEquals("CAMBIAR_ESTADO_CITA", accionCaptor.getValue());
        assertEquals("CITAS", moduloCaptor.getValue());
        assertEquals("sede-chilca-1", sedeCaptor.getValue());
        String detalle = detalleCaptor.getValue();
        assertNotNull(detalle);
        assertTrue(detalle.contains(citaId.toString()));
        assertTrue(detalle.contains("PROGRAMADA → CONFIRMADA"), detalle);
    }

    @Test
    @DisplayName("Cancelar una cita CONFIRMADA sin motivo se rechaza y no guarda ni audita")
    void cancelarConfirmadaSinMotivo() {
        IllegalArgumentException ex1 = assertThrows(IllegalArgumentException.class, () -> citaService.cambiarEstado(citaId, "CANCELADA", null));
        assertNotNull(ex1);
        IllegalArgumentException ex2 = assertThrows(IllegalArgumentException.class, () -> citaService.cambiarEstado(citaId, "CANCELADA", "  ok "));
        assertNotNull(ex2);
        verify(citaRepository, never()).save(any());
        verify(auditoriaPort, never()).registrar(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Cancelar una cita CONFIRMADA con motivo la deja CANCELADA y audita CANCELAR_CITA")
    void cancelarConfirmadaConMotivo() {
        citaPersistida("CONFIRMADA");
        saveDevuelveArgumento();

        CitaResponseDTO cancelada = citaService.cambiarEstado(citaId, "CANCELADA", "Paciente viaja de emergencia");

        assertNotNull(cancelada);
        assertEquals("CANCELADA", cancelada.getEstado());
        assertEquals("Paciente viaja de emergencia", cancelada.getMotivoCancelacion());
        ArgumentCaptor<String> accionCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> moduloCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> sedeCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> detalleCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditoriaPort).registrar(accionCaptor.capture(), moduloCaptor.capture(), sedeCaptor.capture(), detalleCaptor.capture());
        assertEquals("CANCELAR_CITA", accionCaptor.getValue());
        assertEquals("CITAS", moduloCaptor.getValue());
        assertEquals("sede-chilca-1", sedeCaptor.getValue());
        assertNotNull(detalleCaptor.getValue());
        assertTrue(detalleCaptor.getValue().contains("Paciente viaja de emergencia"));
        verify(eventPublisher).publishEvent(any(com.odontomax.agenda.domain.event.CitaCanceladaEvent.class));
    }

    @Test
    @DisplayName("Bloqueada: una cita ATENDIDA no puede pasar a CANCELADA por el endpoint de estado")
    void atendidaNoSeCancela() {
        citaPersistida("ATENDIDA");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> citaService.cambiarEstado(citaId, "CANCELADA", "Paciente pidió anular"));
        assertNotNull(ex);
        assertEquals("ATENDIDA", cita.getEstado());
        verify(citaRepository, never()).save(any());
        verify(auditoriaPort, never()).registrar(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Bloqueada: una cita CANCELADA no se vuelve a cancelar")
    void canceladaNoSeRecancela() {
        citaPersistida("CANCELADA");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> citaService.cambiarEstado(citaId, "CANCELADA", "Segundo intento de cancelación"));
        assertNotNull(ex);
        verify(citaRepository, never()).save(any());
        verify(auditoriaPort, never()).registrar(any(), any(), any(), any());
    }

    @ParameterizedTest(name = "reprogramar desde {0}")
    @ValueSource(strings = {"ATENDIDA", "CANCELADA"})
    @DisplayName("Bloqueada: reprogramar una cita ATENDIDA o CANCELADA es un error de negocio (400), no un 500")
    void reprogramarCerradaEsErrorDeNegocio(String estado) {
        citaPersistida(estado);

        CitaReprogramarRequestDTO dto = CitaReprogramarRequestDTO.builder()
                .nuevaFecha(LocalDate.of(2026, 10, 7))
                .nuevoHorario(LocalTime.of(11, 0))
                .motivo("Paciente pide otro día")
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> citaService.reprogramarCita(citaId, dto));
        assertNotNull(ex);
        verify(citaRepository, never()).save(any());
    }

    @ParameterizedTest(name = "{0} → {1} se rechaza con error de negocio")
    @org.junit.jupiter.params.provider.CsvSource({"ATENDIDA,PROGRAMADA", "CANCELADA,CONFIRMADA", "NO_ASISTIO,CONFIRMADA", "PROGRAMADA,ATENDIDA", "CONFIRMADA,CONFIRMADA"})
    @DisplayName("B-028: una transición fuera del flujo no guarda ni audita")
    void transicionFueraDelFlujo(String origen, String destino) {
        citaPersistida(origen);

        assertThrows(com.odontomax.agenda.domain.exception.TransicionEstadoInvalidaException.class,
                () -> citaService.cambiarEstado(citaId, destino, null));
        assertEquals(origen, cita.getEstado());
        verify(citaRepository, never()).save(any());
        verify(auditoriaPort, never()).registrar(any(), any(), any(), any());
    }

    @Test
    @DisplayName("B-028: el motivo de una transición que no cancela no se guarda como motivo de cancelación")
    void motivoNoSeGuardaComoCancelacion() {
        citaPersistida("PROGRAMADA");
        saveDevuelveArgumento();

        CitaResponseDTO confirmada = citaService.cambiarEstado(citaId, "CONFIRMADA", "Confirmó por teléfono");

        assertNull(confirmada.getMotivoCancelacion());
        assertTrue(confirmada.getNotas().contains("Confirmó por teléfono"));
    }

    @Test
    @DisplayName("Cambiar estado de una cita inexistente lanza error de negocio y no guarda")
    void citaInexistente() {
        when(citaRepository.findByIdParaActualizar(citaId)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> citaService.cambiarEstado(citaId, "CONFIRMADA", null));
        assertNotNull(ex);
        verify(citaRepository, never()).save(any());
        verify(auditoriaPort, never()).registrar(any(), any(), any(), any());
    }
}
