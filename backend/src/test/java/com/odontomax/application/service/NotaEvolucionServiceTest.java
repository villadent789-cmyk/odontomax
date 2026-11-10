package com.odontomax.application.service;

import com.odontomax.application.dto.request.NotaEvolucionRequestDTO;
import com.odontomax.application.dto.response.NotaEvolucionResponseDTO;
import com.odontomax.infrastructure.persistence.entity.AuditoriaLogEntity;
import com.odontomax.infrastructure.persistence.entity.NotaEvolucionEntity;
import com.odontomax.pacientes.infrastructure.persistence.entity.PacienteEntity;
import com.odontomax.infrastructure.persistence.repository.SpringDataAuditoriaRepository;
import com.odontomax.infrastructure.persistence.repository.SpringDataNotaEvolucionRepository;
import com.odontomax.pacientes.infrastructure.persistence.repository.SpringDataPacienteRepository;
import com.odontomax.identidad.api.ClinicaUserPrincipal;
import com.odontomax.agenda.api.ConsultaEspecialistasModuloApi;
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
class NotaEvolucionServiceTest {

    @Mock
    private SpringDataNotaEvolucionRepository notaRepository;

    @Mock
    private SpringDataPacienteRepository pacienteRepository;

    @Mock
    private SpringDataAuditoriaRepository auditoriaRepository;

    @Mock
    private ConsultaEspecialistasModuloApi consultaEspecialistasApi;

    @InjectMocks
    private NotaEvolucionService notaEvolucionService;

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

    private void guardarDevuelveArgumento() {
        when(notaRepository.save(any(NotaEvolucionEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ---------------------------------------------------------------- listar

    @Test
    @DisplayName("Listar: paciente inexistente lanza IllegalArgumentException")
    void testListarPacienteNoExiste() {
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> notaEvolucionService.listarPorPaciente(pacienteId));
        verifyNoInteractions(notaRepository);
    }

    @Test
    @DisplayName("Listar: usuario de otra sede no puede leer las notas clínicas")
    void testListarOtraSede() {
        autenticar(new ClinicaUserPrincipal(UUID.randomUUID(), "rec@odontomax.pe", "Recepción", "RECEPCION", "sede-eltambo"));
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(paciente));

        assertThrows(AccessDeniedException.class, () -> notaEvolucionService.listarPorPaciente(pacienteId));
        verifyNoInteractions(notaRepository);
    }

    @Test
    @DisplayName("Listar: devuelve las notas del paciente mapeadas a DTO")
    void testListarExitoso() {
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(paciente));
        when(notaRepository.findByPacienteIdOrderByCreadoEnDesc(pacienteId)).thenReturn(List.of(
                NotaEvolucionEntity.builder().pacienteId(pacienteId).autorNombre("Dr. Pérez").subjetivo("Dolor").build()));

        List<NotaEvolucionResponseDTO> res = notaEvolucionService.listarPorPaciente(pacienteId);

        assertEquals(1, res.size());
        assertEquals("Dolor", res.get(0).getSubjetivo());
        assertEquals("SOAP", res.get(0).getTipoNota());
    }

    // ---------------------------------------------------------------- crear

    @Test
    @DisplayName("Crear: el autor se toma del usuario en sesión, la nota nace firmada y se audita")
    void testCrearNotaExitoso() {
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(paciente));
        guardarDevuelveArgumento();
        UUID citaId = UUID.randomUUID();

        NotaEvolucionResponseDTO res = notaEvolucionService.crearNota(pacienteId, NotaEvolucionRequestDTO.builder()
                .citaId(citaId).tipoNota("soap").subjetivo("Dolor en 36").objetivo("Caries profunda")
                .analisis("Pulpitis").plan("Endodoncia").build());

        assertEquals(pacienteId, res.getPacienteId());
        assertEquals(citaId, res.getCitaId());
        assertEquals(odontologo.id(), res.getAutorId());
        assertEquals("Dr. Pérez", res.getAutorNombre());
        assertEquals("ODONTOLOGO", res.getAutorRol());
        assertEquals("SOAP", res.getTipoNota());
        assertTrue(res.getFirmado());

        ArgumentCaptor<AuditoriaLogEntity> audit = ArgumentCaptor.forClass(AuditoriaLogEntity.class);
        verify(auditoriaRepository).save(audit.capture());
        assertEquals("CREAR_NOTA_EVOLUCION", audit.getValue().getAccion());
        assertEquals("notas_evolucion", audit.getValue().getTabla());
        assertEquals(odontologo.id(), audit.getValue().getUsuarioId());
    }

    @Test
    @DisplayName("Crear: tipo de nota nulo o en blanco se registra como SOAP")
    void testCrearTipoNotaPorDefecto() {
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(paciente));
        guardarDevuelveArgumento();

        NotaEvolucionResponseDTO enBlanco = notaEvolucionService.crearNota(pacienteId,
                NotaEvolucionRequestDTO.builder().tipoNota("  ").contenido("Control").build());
        NotaEvolucionResponseDTO nulo = notaEvolucionService.crearNota(pacienteId,
                NotaEvolucionRequestDTO.builder().tipoNota(null).contenido("Control").build());

        assertEquals("SOAP", enBlanco.getTipoNota());
        assertEquals("SOAP", nulo.getTipoNota());
    }


    @Test
    @DisplayName("Crear: odontólogo de otra sede recibe AccessDenied")
    void testCrearOtraSede() {
        autenticar(new ClinicaUserPrincipal(UUID.randomUUID(), "doc2@odontomax.pe", "Dr. Otro", "ODONTOLOGO", "sede-eltambo"));
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(paciente));

        assertThrows(AccessDeniedException.class, () ->
                notaEvolucionService.crearNota(pacienteId, NotaEvolucionRequestDTO.builder().contenido("x").build()));
        verifyNoInteractions(notaRepository);
    }

    @Test
    @DisplayName("Crear: corrección que referencia una nota inexistente se rechaza")
    void testCrearCorreccionNotaReferenciaNoExiste() {
        UUID refId = UUID.randomUUID();
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(paciente));
        when(notaRepository.findById(refId)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                notaEvolucionService.crearNota(pacienteId, NotaEvolucionRequestDTO.builder()
                        .notaReferenciaId(refId).motivoCorreccion("Error de pieza").build()));

        assertTrue(ex.getMessage().contains(refId.toString()));
        verify(notaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Crear: corrección que referencia una nota de OTRO paciente se rechaza")
    void testCrearCorreccionNotaDeOtroPaciente() {
        UUID refId = UUID.randomUUID();
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(paciente));
        when(notaRepository.findById(refId)).thenReturn(Optional.of(
                NotaEvolucionEntity.builder().id(refId).pacienteId(UUID.randomUUID()).build()));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                notaEvolucionService.crearNota(pacienteId, NotaEvolucionRequestDTO.builder()
                        .notaReferenciaId(refId).motivoCorreccion("Error de pieza").build()));

        assertTrue(ex.getMessage().contains("no corresponde al paciente"));
        verify(notaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Crear: corrección válida crea una nota nueva enlazada (la original no se modifica)")
    void testCrearCorreccionValida() {
        UUID refId = UUID.randomUUID();
        NotaEvolucionEntity original = NotaEvolucionEntity.builder().id(refId).pacienteId(pacienteId).contenido("Pieza 36").build();
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(paciente));
        when(notaRepository.findById(refId)).thenReturn(Optional.of(original));
        guardarDevuelveArgumento();

        NotaEvolucionResponseDTO res = notaEvolucionService.crearNota(pacienteId, NotaEvolucionRequestDTO.builder()
                .tipoNota("correccion").contenido("Pieza 46").notaReferenciaId(refId).motivoCorreccion("Error de pieza").build());

        assertEquals(refId, res.getNotaReferenciaId());
        assertEquals("Error de pieza", res.getMotivoCorreccion());
        assertEquals("CORRECCION", res.getTipoNota());
        assertEquals("Pieza 36", original.getContenido());
        verify(notaRepository, times(1)).save(any());
    }
    @Test
    @DisplayName("Listar: odontólogo sin cita con el paciente recibe AccessDeniedException")
    void testListarPorPaciente_OdontologoSinCita_Denegado() {
        doThrow(new AccessDeniedException("Acceso denegado: el paciente no está asignado a este odontólogo."))
                .when(consultaEspecialistasApi).exigirAccesoDeOdontologo(pacienteId);

        assertThrows(AccessDeniedException.class, () -> notaEvolucionService.listarPorPaciente(pacienteId));
    }

    @Test
    @DisplayName("Crear: odontólogo sin cita con el paciente recibe AccessDeniedException")
    void testCrearNota_OdontologoSinCita_Denegado() {
        doThrow(new AccessDeniedException("Acceso denegado: el paciente no está asignado a este odontólogo."))
                .when(consultaEspecialistasApi).exigirAccesoDeOdontologo(pacienteId);

        assertThrows(AccessDeniedException.class, () ->
                notaEvolucionService.crearNota(pacienteId, NotaEvolucionRequestDTO.builder().contenido("Nota").build()));
    }
}
