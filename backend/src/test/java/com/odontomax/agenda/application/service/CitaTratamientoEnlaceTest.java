package com.odontomax.agenda.application.service;

import com.odontomax.agenda.application.dto.request.CitaRequestDTO;
import com.odontomax.agenda.application.dto.response.CitaResponseDTO;
import com.odontomax.agenda.application.port.out.CitaRepositoryPort;
import com.odontomax.agenda.application.port.out.DoctorHorarioConsultaPort;
import com.odontomax.agenda.application.port.out.PacienteReservaPort;
import com.odontomax.agenda.application.port.out.PagoAdelantoPort;
import com.odontomax.agenda.application.port.out.RegistroAuditoriaPort;
import com.odontomax.agenda.application.port.out.SaldoFavorPort;
import com.odontomax.agenda.application.port.out.SedeValidacionPort;
import com.odontomax.agenda.application.port.out.TratamientoCitaPort;
import com.odontomax.agenda.application.port.out.TratamientoCitaPort.TratamientoVinculable;
import com.odontomax.agenda.domain.model.Cita;
import com.odontomax.identidad.api.ClinicaUserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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

/** V23: una cita puede ser una sesión de un plan de tratamiento; al atenderla se suma la sesión al plan. */
@ExtendWith(MockitoExtension.class)
class CitaTratamientoEnlaceTest {

    @Mock private CitaRepositoryPort citaRepository;
    @Mock private DoctorHorarioConsultaPort doctorHorarioPort;
    @Mock private RegistroAuditoriaPort auditoriaPort;
    @Mock private SedeValidacionPort sedeValidacionPort;
    @Mock private PagoAdelantoPort pagoAdelantoPort;
    @Mock private SaldoFavorPort saldoFavorPort;
    @Mock private PacienteReservaPort pacienteReservaPort;
    @Mock private com.odontomax.agenda.application.port.out.CitaContactoPort citaContactoPort;
    @Mock private org.springframework.context.ApplicationEventPublisher eventPublisher;
    @Mock private TratamientoCitaPort tratamientoCitaPort;

    @InjectMocks
    private CitaApplicationService citaService;

    private final UUID pacienteId = UUID.randomUUID();
    private final UUID tratamientoId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        // B-032: el servicio consulta al paciente para rellenar sus datos
        lenient().when(pacienteReservaPort.findById(pacienteId)).thenReturn(Optional.of(
                new PacienteReservaPort.PacienteReservaInfo(pacienteId, "12345678", "Luciana Meza", "999888777")));
        ClinicaUserPrincipal principal = new ClinicaUserPrincipal(
                UUID.randomUUID(), "admin@odontomax.com", "Admin", "ADMIN_GENERAL", "sede-chilca-1");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN_GENERAL"))));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private CitaRequestDTO solicitud(UUID tratamiento) {
        return CitaRequestDTO.builder()
                .pacienteId(pacienteId)
                .pacienteNombre("Luciana Meza")
                .fecha(LocalDate.of(2026, 10, 20))
                .hora(LocalTime.of(9, 30))
                .motivoConsulta("Ortodoncia · sesión 3/5")
                .tratamientoId(tratamiento)
                .build();
    }

    private Cita cita(String estado, UUID tratamiento) {
        return Cita.builder()
                .id(UUID.randomUUID())
                .pacienteId(pacienteId)
                .sedeId("sede-chilca-1")
                .fecha(LocalDate.of(2026, 10, 20))
                .hora(LocalTime.of(9, 30))
                .estado(estado)
                .tratamientoId(tratamiento)
                .build();
    }

    @Test
    @DisplayName("Agendar con un plan en proceso del mismo paciente guarda el enlace y lo devuelve")
    void agendarConPlanValido() {
        when(tratamientoCitaPort.buscar(tratamientoId))
                .thenReturn(Optional.of(new TratamientoVinculable(tratamientoId, pacienteId, "EN_PROCESO", "Ortodoncia")));
        when(citaRepository.save(any(Cita.class))).thenAnswer(inv -> inv.getArgument(0));

        CitaResponseDTO creada = citaService.agendarCita(solicitud(tratamientoId), "sede-chilca-1");

        ArgumentCaptor<Cita> guardada = ArgumentCaptor.forClass(Cita.class);
        verify(citaRepository).save(guardada.capture());
        assertEquals(tratamientoId, guardada.getValue().getTratamientoId());
        assertEquals(tratamientoId, creada.getTratamientoId());
    }

    @Test
    @DisplayName("Un plan planificado (aún no empezado) también admite agendar su sesión")
    void agendarConPlanPlanificado() {
        when(tratamientoCitaPort.buscar(tratamientoId))
                .thenReturn(Optional.of(new TratamientoVinculable(tratamientoId, pacienteId, "PLANIFICADO", "Implante")));
        when(citaRepository.save(any(Cita.class))).thenAnswer(inv -> inv.getArgument(0));

        assertEquals(tratamientoId, citaService.agendarCita(solicitud(tratamientoId), "sede-chilca-1").getTratamientoId());
    }

    @Test
    @DisplayName("Sin tratamiento no se consulta el módulo de tratamientos y la cita queda sin enlace")
    void agendarSinPlan() {
        when(citaRepository.save(any(Cita.class))).thenAnswer(inv -> inv.getArgument(0));

        assertNull(citaService.agendarCita(solicitud(null), "sede-chilca-1").getTratamientoId());
        verifyNoInteractions(tratamientoCitaPort);
    }

    @Test
    @DisplayName("Un plan de otro paciente se rechaza con 400 y no se guarda la cita")
    void planDeOtroPaciente() {
        when(tratamientoCitaPort.buscar(tratamientoId))
                .thenReturn(Optional.of(new TratamientoVinculable(tratamientoId, UUID.randomUUID(), "EN_PROCESO", "Ortodoncia")));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> citaService.agendarCita(solicitud(tratamientoId), "sede-chilca-1"));
        assertTrue(ex.getMessage().contains("no pertenece al paciente"));
        verify(citaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Un plan completado o cancelado ya no admite sesiones")
    void planCerrado() {
        when(tratamientoCitaPort.buscar(tratamientoId))
                .thenReturn(Optional.of(new TratamientoVinculable(tratamientoId, pacienteId, "COMPLETADO", "Limpieza")));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> citaService.agendarCita(solicitud(tratamientoId), "sede-chilca-1"));
        assertTrue(ex.getMessage().contains("ya no admite nuevas sesiones"));
        verify(citaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Un plan inexistente se rechaza")
    void planInexistente() {
        when(tratamientoCitaPort.buscar(tratamientoId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> citaService.agendarCita(solicitud(tratamientoId), "sede-chilca-1"));
        verify(citaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Atender una cita enlazada suma la sesión al plan una vez")
    void atenderSumaSesion() {
        Cita enSala = cita("EN_ATENCION", tratamientoId);
        when(citaRepository.findByIdParaActualizar(enSala.getId())).thenReturn(Optional.of(enSala));
        when(citaRepository.save(any(Cita.class))).thenAnswer(inv -> inv.getArgument(0));

        citaService.cambiarEstado(enSala.getId(), "ATENDIDA", null);

        verify(tratamientoCitaPort, times(1)).registrarSesionAtendida(tratamientoId, enSala.getId());
    }

    @Test
    @DisplayName("Atender una cita sin enlace no toca ningún plan")
    void atenderSinEnlace() {
        Cita enSala = cita("EN_ATENCION", null);
        when(citaRepository.findByIdParaActualizar(enSala.getId())).thenReturn(Optional.of(enSala));
        when(citaRepository.save(any(Cita.class))).thenAnswer(inv -> inv.getArgument(0));

        citaService.cambiarEstado(enSala.getId(), "ATENDIDA", null);

        verifyNoInteractions(tratamientoCitaPort);
    }

    @Test
    @DisplayName("Repetir ATENDIDA sobre una cita ya atendida se rechaza (B-028) y no suma otra sesión")
    void atenderDosVecesNoDuplica() {
        Cita atendida = cita("ATENDIDA", tratamientoId);
        when(citaRepository.findByIdParaActualizar(atendida.getId())).thenReturn(Optional.of(atendida));

        assertThrows(IllegalArgumentException.class, () -> citaService.cambiarEstado(atendida.getId(), "ATENDIDA", null));

        verifyNoInteractions(tratamientoCitaPort);
        verify(citaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Otros cambios de estado de una cita enlazada no suman sesión")
    void otrosEstadosNoSuman() {
        Cita confirmada = cita("CONFIRMADA", tratamientoId);
        when(citaRepository.findByIdParaActualizar(confirmada.getId())).thenReturn(Optional.of(confirmada));
        when(citaRepository.save(any(Cita.class))).thenAnswer(inv -> inv.getArgument(0));

        citaService.cambiarEstado(confirmada.getId(), "EN_SALA", null);

        verifyNoInteractions(tratamientoCitaPort);
    }
}
