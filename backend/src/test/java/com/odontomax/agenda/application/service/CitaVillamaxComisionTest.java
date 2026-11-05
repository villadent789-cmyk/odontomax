package com.odontomax.agenda.application.service;

import java.time.Instant;

import com.odontomax.agenda.application.dto.request.CitaCancelarRequestDTO;
import com.odontomax.agenda.application.dto.request.CitaRequestDTO;
import com.odontomax.agenda.application.dto.response.CitaResponseDTO;
import com.odontomax.agenda.application.port.out.*;
import com.odontomax.agenda.domain.model.Cita;
import com.odontomax.agenda.domain.model.Especialista;
import com.odontomax.agenda.domain.model.SedeAgenda;
import com.odontomax.agenda.application.port.out.CitaRepositoryPort;
import com.odontomax.identidad.api.ClinicaUserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CitaVillamaxComisionTest {

    @Mock private CitaRepositoryPort citaRepository;
    @Mock private DoctorHorarioConsultaPort doctorHorarioPort;
    @Mock private RegistroAuditoriaPort auditoriaPort;
    @Mock private SedeValidacionPort sedeValidacionPort;
    @Mock private PagoAdelantoPort pagoAdelantoPort;
    @Mock private SaldoFavorPort saldoFavorPort;
    @Mock private PacienteReservaPort pacienteReservaPort;
    @Mock private CitaContactoPort citaContactoPort;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private TratamientoCitaPort tratamientoCitaPort;
    @Mock private EspecialistaRepositoryPort especialistaRepository;
    @Mock private ComisionCitaPort comisionCitaPort;

    private CitaApplicationService citaService;

    private final UUID pacienteId = UUID.randomUUID();
    private final UUID doctorId = UUID.randomUUID();
    private final UUID doctorDerivadorId = UUID.randomUUID();
    private final String sedeVillamax = "sede-villamax-chilca";

    @BeforeEach
    void setUp() {
        citaService = new CitaApplicationService(
                citaRepository, doctorHorarioPort, auditoriaPort, sedeValidacionPort,
                pagoAdelantoPort, saldoFavorPort, pacienteReservaPort, citaContactoPort,
                eventPublisher, tratamientoCitaPort, especialistaRepository
        );
        citaService.setComisionCitaPort(comisionCitaPort);

        ClinicaUserPrincipal principal = new ClinicaUserPrincipal(
                UUID.randomUUID(), "admin@odontomax.com", "Admin", "ADMIN_GENERAL", sedeVillamax
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN_GENERAL")))
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void configurarSedeVillamax() {
        when(sedeValidacionPort.obtenerConfiguracionSede(sedeVillamax)).thenReturn(Optional.of(
                new SedeValidacionPort.SedeAgendaConfig(
                        sedeVillamax, "Villamax Chilca", "VILLAMAX", true,
                        "964000111", "064-123456", BigDecimal.ZERO, 0, 24, 24, LocalTime.of(8, 0), 30
                )
        ));
        when(sedeValidacionPort.obtenerSede(sedeVillamax)).thenReturn(Optional.of(
                new SedeAgenda(sedeVillamax, true, 1)
        ));
        when(doctorHorarioPort.obtenerNombreDoctor(doctorId)).thenReturn("Dra. Radióloga Villamax");
        lenient().when(especialistaRepository.findById(doctorId)).thenReturn(Optional.of(
                Especialista.builder().id(doctorId).sedes(List.of(sedeVillamax)).sillonesPorSede(Map.of(sedeVillamax, 1)).build()
        ));
        lenient().when(doctorHorarioPort.esDoctorActivo(doctorId)).thenReturn(true);
        lenient().when(doctorHorarioPort.atiendeEnHorarioYSede(any(), any(), any(), any(), anyInt())).thenReturn(true);
        lenient().when(pacienteReservaPort.findById(pacienteId)).thenReturn(Optional.of(
                new PacienteReservaPort.PacienteReservaInfo(pacienteId, "12345678", "Juan Pérez", "987654321")
        ));
    }

    private CitaRequestDTO.CitaRequestDTOBuilder citaRequestBuilder() {
        return CitaRequestDTO.builder()
                .pacienteId(pacienteId)
                .doctorId(doctorId)
                .sedeId(sedeVillamax)
                .fecha(LocalDate.now().plusDays(1))
                .hora(LocalTime.of(10, 0))
                .duracionMinutos(15)
                .motivoConsulta("Radiografía panorámica")
                .sillonNumero(1);
    }

    @Test
    @DisplayName("En Villamax el doctor derivador es obligatorio salvo que sea paciente directo")
    void agendarCita_enVillamax_sinDerivadorNiPacienteDirecto_lanzaError() {
        configurarSedeVillamax();

        CitaRequestDTO req = citaRequestBuilder().build();

        assertThatThrownBy(() -> citaService.agendarCita(req, sedeVillamax))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("En Villamax el doctor derivador es obligatorio");
    }

    @Test
    @DisplayName("En Villamax agendar con doctor derivador registra la cita con el ID del derivador")
    void agendarCita_enVillamax_conDoctorDerivador_guardaDerivador() {
        configurarSedeVillamax();
        when(citaRepository.save(any(Cita.class))).thenAnswer(i -> i.getArgument(0));

        CitaRequestDTO req = citaRequestBuilder()
                .doctorDerivadorId(doctorDerivadorId)
                .pacienteDirecto(false)
                .build();

        CitaResponseDTO res = citaService.agendarCita(req, sedeVillamax);

        assertThat(res.getDoctorDerivadorId()).isEqualTo(doctorDerivadorId);
        assertThat(res.getPacienteDirecto()).isFalse();

        ArgumentCaptor<Cita> captor = ArgumentCaptor.forClass(Cita.class);
        verify(citaRepository).save(captor.capture());
        assertThat(captor.getValue().getDoctorDerivadorId()).isEqualTo(doctorDerivadorId);
        assertThat(captor.getValue().isPacienteDirecto()).isFalse();
    }

    @Test
    @DisplayName("En Villamax agendar con paciente directo sin derivación se registra sin doctor derivador")
    void agendarCita_enVillamax_conPacienteDirecto_guardaPacienteDirecto() {
        configurarSedeVillamax();
        when(citaRepository.save(any(Cita.class))).thenAnswer(i -> i.getArgument(0));

        CitaRequestDTO req = citaRequestBuilder()
                .pacienteDirecto(true)
                .build();

        CitaResponseDTO res = citaService.agendarCita(req, sedeVillamax);

        assertThat(res.getDoctorDerivadorId()).isNull();
        assertThat(res.getPacienteDirecto()).isTrue();

        ArgumentCaptor<Cita> captor = ArgumentCaptor.forClass(Cita.class);
        verify(citaRepository).save(captor.capture());
        assertThat(captor.getValue().getDoctorDerivadorId()).isNull();
        assertThat(captor.getValue().isPacienteDirecto()).isTrue();
    }

    @Test
    @DisplayName("Al pasar a ATENDIDA una cita, se invoca a ComisionCitaPort para generar la comisión")
    void cambiarEstado_atendida_invocaGeneracionComision() {
        UUID citaId = UUID.randomUUID();
        Cita cita = Cita.builder()
                .id(citaId)
                .sedeId(sedeVillamax)
                .estado("EN_ATENCION")
                .inicioAtencionEn(Instant.now())
                .doctorDerivadorId(doctorDerivadorId)
                .pacienteDirecto(false)
                .build();

        when(citaRepository.findByIdParaActualizar(citaId)).thenReturn(Optional.of(cita));
        when(citaRepository.save(any(Cita.class))).thenAnswer(i -> i.getArgument(0));

        citaService.cambiarEstado(citaId, "ATENDIDA", null);

        verify(comisionCitaPort).generarComisionSiAplica(citaId);
    }

    @Test
    @DisplayName("Al cancelar una cita, se anula la comisión pendiente si existía")
    void anularComision_alCancelarCita_anulaComisionPendiente() {
        UUID citaId = UUID.randomUUID();
        Cita cita = Cita.builder()
                .id(citaId)
                .sedeId(sedeVillamax)
                .estado("PROGRAMADA")
                .adelantoMonto(BigDecimal.ZERO)
                .build();

        when(citaRepository.findByIdParaActualizar(citaId)).thenReturn(Optional.of(cita));
        lenient().when(sedeValidacionPort.obtenerConfiguracionSede(sedeVillamax)).thenReturn(Optional.of(
                new SedeValidacionPort.SedeAgendaConfig(sedeVillamax, "Villamax", "VILLAMAX", true, null, null, null, 0, 24, 0, null, 0)
        ));
        when(citaRepository.save(any(Cita.class))).thenAnswer(i -> i.getArgument(0));

        citaService.cancelarCita(citaId, CitaCancelarRequestDTO.builder().motivo("Paciente canceló por viaje").build());

        verify(comisionCitaPort).anularComisionSiAplica(eq(citaId), contains("Paciente canceló por viaje"));
    }
}
