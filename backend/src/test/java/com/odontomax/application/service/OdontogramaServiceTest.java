package com.odontomax.application.service;

import com.odontomax.application.dto.request.OdontogramaLoteRequestDTO;
import com.odontomax.application.dto.request.OdontogramaPiezaRequestDTO;
import com.odontomax.application.dto.response.OdontogramaRegistroResponseDTO;
import com.odontomax.infrastructure.persistence.entity.AuditoriaLogEntity;
import com.odontomax.infrastructure.persistence.entity.OdontogramaRegistroEntity;
import com.odontomax.pacientes.infrastructure.persistence.entity.PacienteEntity;
import com.odontomax.infrastructure.persistence.repository.SpringDataAuditoriaRepository;
import com.odontomax.infrastructure.persistence.repository.SpringDataOdontogramaRepository;
import com.odontomax.pacientes.infrastructure.persistence.repository.SpringDataPacienteRepository;
import com.odontomax.agenda.api.ConsultaEspecialistasModuloApi;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OdontogramaServiceTest {

    @Mock
    private SpringDataOdontogramaRepository odontogramaRepository;

    @Mock
    private SpringDataPacienteRepository pacienteRepository;

    @Mock
    private SpringDataAuditoriaRepository auditoriaRepository;

    @Mock
    private ConsultaEspecialistasModuloApi consultaEspecialistasApi;

    @InjectMocks
    private OdontogramaService odontogramaService;

    private UUID pacienteId;
    private PacienteEntity paciente;
    private ClinicaUserPrincipal odontologo;

    @BeforeEach
    void setUp() {
        pacienteId = UUID.randomUUID();
        paciente = PacienteEntity.builder()
                .id(pacienteId)
                .dni("45678912")
                .nombres("María")
                .apellidos("Flores")
                .sedeId("sede-chilca-1")
                .build();
        odontologo = new ClinicaUserPrincipal(UUID.randomUUID(), "doc@odontomax.pe", "Dr. Pérez", "ODONTOLOGO", "sede-chilca-1");
        autenticar(odontologo);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void autenticar(ClinicaUserPrincipal principal) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + principal.rol()))));
    }

    private OdontogramaPiezaRequestDTO pieza(int fdi, String estado, List<String> caras) {
        return OdontogramaPiezaRequestDTO.builder().piezaFdi(fdi).estado(estado).caras(caras).build();
    }

    private void guardarDevuelveArgumento() {
        when(odontogramaRepository.save(any(OdontogramaRegistroEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ---------------------------------------------------------------- lectura

    @Test
    @DisplayName("Estado actual: paciente inexistente lanza IllegalArgumentException")
    void testEstadoActualPacienteNoExiste() {
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> odontogramaService.obtenerEstadoActual(pacienteId));
        verifyNoInteractions(odontogramaRepository);
    }

    @Test
    @DisplayName("Estado actual: usuario de otra sede no puede ver el odontograma")
    void testEstadoActualOtraSede() {
        autenticar(new ClinicaUserPrincipal(UUID.randomUUID(), "doc2@odontomax.pe", "Dr. Otro", "ODONTOLOGO", "sede-eltambo"));
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(paciente));

        assertThrows(AccessDeniedException.class, () -> odontogramaService.obtenerEstadoActual(pacienteId));
        verifyNoInteractions(odontogramaRepository);
    }

    @Test
    @DisplayName("Estado actual: RECEPCION de la misma sede puede leer y caras nulas se mapean a lista vacía")
    void testEstadoActualLecturaRecepcion() {
        autenticar(new ClinicaUserPrincipal(UUID.randomUUID(), "rec@odontomax.pe", "Recepción", "RECEPCION", "sede-chilca-1"));
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(paciente));
        when(odontogramaRepository.findEstadoActualPorPaciente(pacienteId)).thenReturn(List.of(
                OdontogramaRegistroEntity.builder().pacienteId(pacienteId).piezaFdi(11).estado("caries").caras(null).build()));

        List<OdontogramaRegistroResponseDTO> res = odontogramaService.obtenerEstadoActual(pacienteId);

        assertEquals(1, res.size());
        assertEquals(11, res.get(0).getPiezaFdi());
        assertEquals(List.of(), res.get(0).getCaras());
    }

    @Test
    @DisplayName("Historial: ADMIN_GENERAL accede a cualquier sede")
    void testHistorialAdminGeneral() {
        autenticar(new ClinicaUserPrincipal(UUID.randomUUID(), "admin@odontomax.pe", "Admin", "ADMIN_GENERAL", null));
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(paciente));
        when(odontogramaRepository.findByPacienteIdOrderByRegistradoEnDesc(pacienteId)).thenReturn(List.of());

        assertTrue(odontogramaService.obtenerHistorial(pacienteId).isEmpty());
        verify(odontogramaRepository).findByPacienteIdOrderByRegistradoEnDesc(pacienteId);
    }

    // ---------------------------------------------------------------- escritura por pieza

    @Test
    @DisplayName("Registrar pieza: normaliza el estado, guarda caras, autor y audita ACTUALIZAR_PIEZA")
    void testRegistrarPiezaExitoso() {
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(paciente));
        guardarDevuelveArgumento();

        OdontogramaRegistroResponseDTO res = odontogramaService.registrarPieza(pacienteId,
                pieza(36, "  CARIES ", List.of("O", "M")));

        assertEquals("caries", res.getEstado());
        assertEquals(List.of("O", "M"), res.getCaras());
        assertEquals(odontologo.id(), res.getRegistradoPor());
        assertEquals(pacienteId, res.getPacienteId());

        ArgumentCaptor<AuditoriaLogEntity> audit = ArgumentCaptor.forClass(AuditoriaLogEntity.class);
        verify(auditoriaRepository).save(audit.capture());
        assertEquals("ACTUALIZAR_PIEZA", audit.getValue().getAccion());
        assertEquals("ODONTOGRAMA", audit.getValue().getModulo());
        assertEquals("sede-chilca-1", audit.getValue().getSedeId());
        assertTrue(audit.getValue().getDetalle().contains("Pieza 36"));
    }

    @Test
    @DisplayName("Registrar pieza: caras nulas se guardan como arreglo vacío")
    void testRegistrarPiezaSinCaras() {
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(paciente));
        guardarDevuelveArgumento();

        odontogramaService.registrarPieza(pacienteId, pieza(11, "sano", null));

        ArgumentCaptor<OdontogramaRegistroEntity> captor = ArgumentCaptor.forClass(OdontogramaRegistroEntity.class);
        verify(odontogramaRepository).save(captor.capture());
        assertArrayEquals(new String[0], captor.getValue().getCaras());
    }


    @Test
    @DisplayName("Registrar pieza: ADMIN_SEDE de su sede puede escribir")
    void testRegistrarPiezaAdminSede() {
        autenticar(new ClinicaUserPrincipal(UUID.randomUUID(), "as@odontomax.pe", "Admin Sede", "ADMIN_SEDE", "sede-chilca-1"));
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(paciente));
        guardarDevuelveArgumento();

        assertNotNull(odontogramaService.registrarPieza(pacienteId, pieza(21, "corona", null)));
    }

    @Test
    @DisplayName("Registrar pieza: odontólogo de otra sede recibe AccessDenied antes de validar el rol")
    void testRegistrarPiezaOdontologoOtraSede() {
        autenticar(new ClinicaUserPrincipal(UUID.randomUUID(), "doc2@odontomax.pe", "Dr. Otro", "ODONTOLOGO", "sede-eltambo"));
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(paciente));

        assertThrows(AccessDeniedException.class,
                () -> odontogramaService.registrarPieza(pacienteId, pieza(11, "caries", null)));
        verifyNoInteractions(odontogramaRepository);
    }

    @Test
    @DisplayName("Registrar pieza: sin autenticación lanza AccessDenied")
    void testRegistrarPiezaSinAutenticar() {
        SecurityContextHolder.clearContext();
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(paciente));

        assertThrows(AccessDeniedException.class,
                () -> odontogramaService.registrarPieza(pacienteId, pieza(11, "caries", null)));
        verifyNoInteractions(odontogramaRepository);
    }

    @Test
    @DisplayName("Registrar pieza: un fallo al auditar no revierte el registro clínico")
    void testRegistrarPiezaFalloAuditoria() {
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(paciente));
        guardarDevuelveArgumento();
        when(auditoriaRepository.save(any())).thenThrow(new RuntimeException("auditoría caída"));

        assertDoesNotThrow(() -> odontogramaService.registrarPieza(pacienteId, pieza(11, "caries", null)));
        verify(odontogramaRepository).save(any());
    }

    // ---------------------------------------------------------------- escritura por lote

    @Test
    @DisplayName("Registrar lote: guarda cada pieza y registra una sola auditoría con el total")
    void testRegistrarLoteExitoso() {
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(paciente));
        guardarDevuelveArgumento();

        OdontogramaLoteRequestDTO lote = OdontogramaLoteRequestDTO.builder().piezas(List.of(
                pieza(11, "Caries", List.of("V")),
                pieza(12, "SANO", null),
                pieza(46, "endodoncia", List.of("O")))).build();

        List<OdontogramaRegistroResponseDTO> res = odontogramaService.registrarLote(pacienteId, lote);

        assertEquals(3, res.size());
        assertEquals(List.of("caries", "sano", "endodoncia"),
                res.stream().map(OdontogramaRegistroResponseDTO::getEstado).toList());
        verify(odontogramaRepository, times(3)).save(any());

        ArgumentCaptor<AuditoriaLogEntity> audit = ArgumentCaptor.forClass(AuditoriaLogEntity.class);
        verify(auditoriaRepository, times(1)).save(audit.capture());
        assertEquals("ACTUALIZAR_LOTE_ODONTOGRAMA", audit.getValue().getAccion());
        assertTrue(audit.getValue().getDetalle().contains("3 pieza(s)"));
    }

    @Test
    @DisplayName("Estado actual: odontólogo sin cita con el paciente recibe AccessDeniedException")
    void testObtenerEstadoActual_OdontologoSinCita_Denegado() {
        doThrow(new AccessDeniedException("Acceso denegado: el paciente no está asignado a este odontólogo."))
                .when(consultaEspecialistasApi).exigirAccesoDeOdontologo(pacienteId);

        assertThrows(AccessDeniedException.class, () -> odontogramaService.obtenerEstadoActual(pacienteId));
    }

    @Test
    @DisplayName("Registrar pieza: odontólogo sin cita con el paciente recibe AccessDeniedException")
    void testRegistrarPieza_OdontologoSinCita_Denegado() {
        doThrow(new AccessDeniedException("Acceso denegado: el paciente no está asignado a este odontólogo."))
                .when(consultaEspecialistasApi).exigirAccesoDeOdontologo(pacienteId);

        assertThrows(AccessDeniedException.class, () -> odontogramaService.registrarPieza(pacienteId, pieza(11, "caries", null)));
    }
}
