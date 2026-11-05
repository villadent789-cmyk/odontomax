package com.odontomax.pacientes.application.service;

import com.odontomax.application.dto.response.*;
import com.odontomax.identidad.api.ClinicaUserPrincipal;
import com.odontomax.pacientes.application.dto.response.*;
import com.odontomax.pacientes.application.port.in.GestionarAccesoHistoriaUseCase;
import com.odontomax.pacientes.application.port.in.GestionarConsentimientosUseCase;
import com.odontomax.pacientes.application.port.out.ExpedienteSeccionesPort;
import com.odontomax.pacientes.application.port.out.HistorialCitasPort;
import com.odontomax.pacientes.application.port.out.HistorialCitasPort;
import com.odontomax.pacientes.application.port.out.RegistroAuditoriaPort;
import com.odontomax.pacientes.domain.exception.PacienteNoEncontradoException;
import com.odontomax.pacientes.infrastructure.persistence.entity.PacienteEntity;
import com.odontomax.pacientes.infrastructure.persistence.repository.SpringDataPacienteRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExportarExpedienteServiceTest {

    @Mock
    private SpringDataPacienteRepository pacienteRepository;

    @Mock
    private HistorialCitasPort historialCitasPort;

    @Mock
    private ExpedienteSeccionesPort expedienteSeccionesPort;

    @Mock
    private GestionarConsentimientosUseCase consentimientoService;

    @Mock
    private GestionarAccesoHistoriaUseCase historiaClinicaAccesoService;

    @Mock
    private RegistroAuditoriaPort registroAuditoria;

    @InjectMocks
    private ExportarExpedienteService exportarService;

    private UUID pacienteId;
    private PacienteEntity paciente;

    @BeforeEach
    void setUp() {
        pacienteId = UUID.randomUUID();
        paciente = PacienteEntity.builder()
                .id(pacienteId)
                .dni("45678912")
                .nombres("Luciana")
                .apellidos("Meza")
                .sedeId("sede-chilca-1")
                .alergias("Penicilina, Latex")
                .build();

        ClinicaUserPrincipal admin = new ClinicaUserPrincipal(
                UUID.randomUUID(), "admin@odontomax.pe", "Admin", "ADMIN_GENERAL", null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(admin, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Exporta el expediente completo con las 8 secciones y registra auditoria ARCO")
    void exportarExitoso() {
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(paciente));
        when(historialCitasPort.citasDePaciente(pacienteId)).thenReturn(List.of(new CitaResponseDTO()));
        when(expedienteSeccionesPort.listarTratamientosPorPaciente(pacienteId)).thenReturn(List.of(new TratamientoResponseDTO()));
        when(expedienteSeccionesPort.listarPagosPorPaciente(pacienteId)).thenReturn(List.of(new PagoResponseDTO()));
        when(expedienteSeccionesPort.listarNotasPorPaciente(pacienteId)).thenReturn(List.of(new NotaEvolucionResponseDTO()));
        when(expedienteSeccionesPort.obtenerOdontogramaPorPaciente(pacienteId)).thenReturn(List.of(new OdontogramaRegistroResponseDTO()));
        when(consentimientoService.listarPorPaciente(pacienteId)).thenReturn(List.of(new ConsentimientoResponseDTO()));
        when(historiaClinicaAccesoService.listarAccesos(pacienteId)).thenReturn(List.of(new HistoriaClinicaAccesoResponseDTO()));

        PacienteExportDTO expediente = exportarService.exportarExpedienteCompleto(pacienteId);

        assertNotNull(expediente);
        assertEquals("Luciana", expediente.getDatosPersonales().getNombres());
        // B-049: Alergias con espacios normalizados
        assertEquals(List.of("Penicilina", "Latex"), expediente.getDatosPersonales().getAlergias());
        assertEquals(1, expediente.getCitas().size());
        assertEquals(1, expediente.getTratamientos().size());
        assertEquals(1, expediente.getPagos().size());
        assertEquals(1, expediente.getNotasEvolucion().size());
        assertEquals(1, expediente.getOdontograma().size());
        assertEquals(1, expediente.getConsentimientos().size());
        assertEquals(1, expediente.getAccesosAuditoria().size());
        assertNotNull(expediente.getGeneradoEn());
        assertNotNull(expediente.getAvisoLegal());

        verify(registroAuditoria).registrarExportacionArco(
                any(), eq("sede-chilca-1"), eq(pacienteId), contains("Luciana Meza"), anyString());
    }

    @Test
    @DisplayName("Paciente no encontrado lanza PacienteNoEncontradoException")
    void pacienteNoEncontrado() {
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.empty());

        assertThrows(PacienteNoEncontradoException.class,
                () -> exportarService.exportarExpedienteCompleto(pacienteId));
        verify(historialCitasPort).exigirAccesoDeOdontologo(pacienteId);
        verifyNoInteractions(expedienteSeccionesPort, registroAuditoria);
    }

    @Test
    @DisplayName("Acceso a paciente de otra sede lanza AccessDeniedException")
    void accesoOtraSede() {
        ClinicaUserPrincipal doctor = new ClinicaUserPrincipal(
                UUID.randomUUID(), "doc@odontomax.pe", "Doc", "ODONTOLOGO", "sede-eltambo");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(doctor, null, List.of()));

        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(paciente));

        assertThrows(AccessDeniedException.class,
                () -> exportarService.exportarExpedienteCompleto(pacienteId));
        verify(historialCitasPort).exigirAccesoDeOdontologo(pacienteId);
        verifyNoInteractions(expedienteSeccionesPort, registroAuditoria);
    }
    @Test
    @DisplayName("Odontólogo sin cita con el paciente recibe AccessDeniedException")
    void odontologoSinCita_Denegado() {
        ClinicaUserPrincipal doctor = new ClinicaUserPrincipal(
                UUID.randomUUID(), "doc@odontomax.pe", "Doc", "ODONTOLOGO", "sede-chilca-1");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(doctor, null, List.of()));

        doThrow(new AccessDeniedException("Acceso denegado: el paciente no está asignado a este odontólogo."))
                .when(historialCitasPort).exigirAccesoDeOdontologo(pacienteId);

        assertThrows(AccessDeniedException.class,
                () -> exportarService.exportarExpedienteCompleto(pacienteId));
        verify(historialCitasPort).exigirAccesoDeOdontologo(pacienteId);
    }
}
