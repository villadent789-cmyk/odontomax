package com.odontomax.clinica.service;

import com.odontomax.agenda.api.ConsultaEspecialistasModuloApi;
import com.odontomax.identidad.api.ClinicaUserPrincipal;
import com.odontomax.infrastructure.persistence.repository.SpringDataOdontogramaRepository;
import com.odontomax.pacientes.api.ConsultaPacientesModuloApi;
import com.odontomax.pacientes.api.PacienteResumen;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OdontogramaPdfServiceTest {

    @Mock
    private SpringDataOdontogramaRepository odontogramaRepository;

    @Mock
    private ConsultaPacientesModuloApi pacientesApi;

    @Mock
    private ConsultaEspecialistasModuloApi consultaEspecialistasApi;

    @InjectMocks
    private OdontogramaPdfService odontogramaPdfService;

    private UUID pacienteId;
    private PacienteResumen paciente;

    @BeforeEach
    void setUp() {
        pacienteId = UUID.randomUUID();
        paciente = new PacienteResumen(pacienteId, "71234567", "Juan", "Perez", "987654321", "sede-chilca-1", LocalDate.of(1990, 1, 1), "WEB", null, 0);
        ClinicaUserPrincipal principal = new ClinicaUserPrincipal(
                UUID.randomUUID(), "doc@odontomax.pe", "Dr. Test", "ODONTOLOGO", "sede-chilca-1");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority("ROLE_ODONTOLOGO"))));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("generarPdf: genera bytes de PDF válidos cuando tiene permiso")
    void generarPdf_Exito() {
        when(pacientesApi.findById(pacienteId)).thenReturn(Optional.of(paciente));
        when(odontogramaRepository.findEstadoActualPorPaciente(pacienteId)).thenReturn(List.of());

        byte[] pdf = odontogramaPdfService.generarPdf(pacienteId);
        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
        verify(consultaEspecialistasApi).exigirAccesoDeOdontologo(pacienteId);
    }

    @Test
    @DisplayName("generarPdf: lanza AccessDeniedException cuando odontólogo no tiene cita con el paciente")
    void generarPdf_OdontologoSinCita_Denegado() {
        when(pacientesApi.findById(pacienteId)).thenReturn(Optional.of(paciente));
        doThrow(new AccessDeniedException("Acceso denegado: el paciente no está asignado a este odontólogo."))
                .when(consultaEspecialistasApi).exigirAccesoDeOdontologo(pacienteId);

        assertThrows(AccessDeniedException.class, () -> odontogramaPdfService.generarPdf(pacienteId));
    }
}
