package com.odontomax.pacientes.application.service;

import com.odontomax.identidad.api.ClinicaUserPrincipal;
import com.odontomax.pacientes.application.dto.response.HistoriaClinicaAccesoResponseDTO;
import com.odontomax.pacientes.application.port.out.AccesoHistoriaRepositoryPort;
import com.odontomax.pacientes.application.port.out.HistorialCitasPort;
import com.odontomax.pacientes.application.port.out.RegistroAuditoriaPort;
import com.odontomax.pacientes.domain.model.AccesoHistoria;
import com.odontomax.pacientes.infrastructure.persistence.entity.PacienteEntity;
import com.odontomax.pacientes.infrastructure.persistence.repository.SpringDataPacienteRepository;
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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccesoHistoriaApplicationServiceTest {

    @Mock
    private AccesoHistoriaRepositoryPort accesoRepository;

    @Mock
    private SpringDataPacienteRepository pacienteRepository;

    @Mock
    private RegistroAuditoriaPort registroAuditoria;

    @Mock
    private HistorialCitasPort historialCitas;

    @InjectMocks
    private AccesoHistoriaApplicationService accesoService;

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

    private AccesoHistoria acceso(UUID usuarioId, String nombre) {
        return AccesoHistoria.builder()
                .id(UUID.randomUUID())
                .pacienteId(pacienteId)
                .usuarioId(usuarioId)
                .usuarioNombre(nombre)
                .usuarioEmail("x@odontomax.pe")
                .usuarioRol("ODONTOLOGO")
                .sedeId("sede-chilca-1")
                .ip("10.0.0.9")
                .accedidoEn(OffsetDateTime.now())
                .build();
    }

    // ---------------------------------------------------------------- registrarApertura

    @Test
    @DisplayName("Apertura: primera apertura en la ventana de 5 min registra acceso y auditoría APERTURA_HISTORIA")
    void testAperturaRegistraAcceso() {
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(paciente));
        when(accesoRepository.findFirstByPacienteIdAndUsuarioIdAndAccedidoEnAfterOrderByAccedidoEnDesc(
                eq(pacienteId), eq(odontologo.id()), any())).thenReturn(Optional.empty());
        when(accesoRepository.save(any(AccesoHistoria.class))).thenAnswer(inv -> inv.getArgument(0));

        HistoriaClinicaAccesoResponseDTO res = accesoService.registrarApertura(pacienteId);

        assertEquals(odontologo.id(), res.getUsuarioId());
        assertEquals("doc@odontomax.pe", res.getUsuarioEmail());
        assertEquals("ODONTOLOGO", res.getUsuarioRol());
        assertEquals("sede-chilca-1", res.getSedeId());
        assertEquals("127.0.0.1", res.getIp());

        verify(registroAuditoria).registrarAccesoHistoria(
                eq(odontologo.id()),
                eq("Dr. Pérez"),
                eq("ODONTOLOGO"),
                eq("sede-chilca-1"),
                eq(pacienteId),
                eq("APERTURA_HISTORIA"),
                contains("María Flores"),
                eq("127.0.0.1")
        );
    }

    @Test
    @DisplayName("Apertura: la ventana de deduplicación consultada es de ~5 minutos hacia atrás")
    void testAperturaVentanaCincoMinutos() {
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(paciente));
        when(accesoRepository.save(any(AccesoHistoria.class))).thenAnswer(inv -> inv.getArgument(0));

        OffsetDateTime antes = OffsetDateTime.now().minusMinutes(5);
        accesoService.registrarApertura(pacienteId);
        OffsetDateTime despues = OffsetDateTime.now().minusMinutes(5);

        ArgumentCaptor<OffsetDateTime> ventana = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(accesoRepository).findFirstByPacienteIdAndUsuarioIdAndAccedidoEnAfterOrderByAccedidoEnDesc(
                eq(pacienteId), eq(odontologo.id()), ventana.capture());
        assertFalse(ventana.getValue().isBefore(antes));
        assertFalse(ventana.getValue().isAfter(despues));
    }

    @Test
    @DisplayName("Apertura: reapertura propia dentro de la ventana no duplica el registro ni la auditoría")
    void testAperturaDeduplicada() {
        AccesoHistoria previo = acceso(odontologo.id(), "Dr. Pérez");
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(paciente));
        when(accesoRepository.findFirstByPacienteIdAndUsuarioIdAndAccedidoEnAfterOrderByAccedidoEnDesc(
                eq(pacienteId), eq(odontologo.id()), any())).thenReturn(Optional.of(previo));

        HistoriaClinicaAccesoResponseDTO res = accesoService.registrarApertura(pacienteId);

        assertEquals(previo.getId(), res.getId());
        verify(accesoRepository, never()).save(any());
        verifyNoInteractions(registroAuditoria);
    }

    @Test
    @DisplayName("Apertura: al deduplicar devuelve el acceso del propio usuario, no el de otro más reciente")
    void testAperturaDeduplicadaDevuelveAccesoPropio() {
        AccesoHistoria deOtro = acceso(UUID.randomUUID(), "Dra. Otra");
        AccesoHistoria propio = acceso(odontologo.id(), "Dr. Pérez");
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(paciente));
        lenient().when(accesoRepository.findByPacienteIdOrderByAccedidoEnDesc(pacienteId)).thenReturn(List.of(deOtro, propio));
        when(accesoRepository.findFirstByPacienteIdAndUsuarioIdAndAccedidoEnAfterOrderByAccedidoEnDesc(
                eq(pacienteId), eq(odontologo.id()), any())).thenReturn(Optional.of(propio));

        HistoriaClinicaAccesoResponseDTO res = accesoService.registrarApertura(pacienteId);

        assertEquals(propio.getId(), res.getId());
        assertEquals(odontologo.id(), res.getUsuarioId());
        verify(accesoRepository, never()).findByPacienteIdOrderByAccedidoEnDesc(any());
        verify(accesoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Apertura: si solo OTRO usuario abrió hace menos de 5 min, se registra un acceso nuevo del usuario actual")
    void testAperturaTrasAccesoDeOtroUsuarioRegistraNuevo() {
        AccesoHistoria deOtro = acceso(UUID.randomUUID(), "Dra. Otra");
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(paciente));
        lenient().when(accesoRepository.findByPacienteIdOrderByAccedidoEnDesc(pacienteId)).thenReturn(List.of(deOtro));
        lenient().when(accesoRepository.existsByPacienteIdAndUsuarioIdAndAccedidoEnAfter(eq(pacienteId), eq(deOtro.getUsuarioId()), any()))
                .thenReturn(true);
        when(accesoRepository.findFirstByPacienteIdAndUsuarioIdAndAccedidoEnAfterOrderByAccedidoEnDesc(
                eq(pacienteId), eq(odontologo.id()), any())).thenReturn(Optional.empty());
        when(accesoRepository.save(any(AccesoHistoria.class))).thenAnswer(inv -> inv.getArgument(0));

        HistoriaClinicaAccesoResponseDTO res = accesoService.registrarApertura(pacienteId);

        ArgumentCaptor<AccesoHistoria> captor = ArgumentCaptor.forClass(AccesoHistoria.class);
        verify(accesoRepository).save(captor.capture());
        assertEquals(odontologo.id(), captor.getValue().getUsuarioId());
        assertEquals(odontologo.id(), res.getUsuarioId());
        assertNotEquals(deOtro.getId(), res.getId());
        verify(registroAuditoria).registrarAccesoHistoria(
                eq(odontologo.id()), anyString(), anyString(), eq("sede-chilca-1"), eq(pacienteId), eq("APERTURA_HISTORIA"), anyString(), anyString());
    }

    @Test
    @DisplayName("Apertura: usuario de otra sede no puede abrir la historia clínica")
    void testAperturaOtraSede() {
        autenticar(new ClinicaUserPrincipal(UUID.randomUUID(), "doc2@odontomax.pe", "Dr. Otro", "ODONTOLOGO", "sede-eltambo"));
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(paciente));

        assertThrows(AccessDeniedException.class, () -> accesoService.registrarApertura(pacienteId));
        verifyNoInteractions(accesoRepository, registroAuditoria);
    }

    @Test
    @DisplayName("Apertura: sin autenticación se deniega (no se registra un acceso anónimo)")
    void testAperturaSinAutenticar() {
        SecurityContextHolder.clearContext();
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(paciente));

        assertThrows(AccessDeniedException.class, () -> accesoService.registrarApertura(pacienteId));
        verifyNoInteractions(accesoRepository);
    }

    @Test
    @DisplayName("Apertura: paciente inexistente lanza IllegalArgumentException")
    void testAperturaPacienteNoExiste() {
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> accesoService.registrarApertura(pacienteId));
        verifyNoInteractions(accesoRepository);
    }

    @Test
    @DisplayName("Apertura: un fallo en la auditoría general no impide registrar el acceso")
    void testAperturaFalloAuditoria() {
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(paciente));
        when(accesoRepository.save(any(AccesoHistoria.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("auditoría caída")).when(registroAuditoria).registrarAccesoHistoria(any(), any(), any(), any(), any(), any(), any(), any());

        assertNotNull(accesoService.registrarApertura(pacienteId));
        verify(accesoRepository).save(any());
    }

    // ---------------------------------------------------------------- registrarLecturaDeduplicada

    @Test
    @DisplayName("Lectura: usuario o paciente nulo no hace nada")
    void testLecturaArgumentosNulos() {
        accesoService.registrarLecturaDeduplicada(pacienteId, null, "10.0.0.1");
        accesoService.registrarLecturaDeduplicada(null, odontologo, "10.0.0.1");

        verifyNoInteractions(accesoRepository, pacienteRepository, registroAuditoria);
    }

    @Test
    @DisplayName("Lectura: si ya hubo acceso en la ventana no se registra de nuevo")
    void testLecturaDeduplicada() {
        when(accesoRepository.existsByPacienteIdAndUsuarioIdAndAccedidoEnAfter(eq(pacienteId), eq(odontologo.id()), any()))
                .thenReturn(true);

        accesoService.registrarLecturaDeduplicada(pacienteId, odontologo, "10.0.0.1");

        verify(accesoRepository, never()).save(any());
        verifyNoInteractions(pacienteRepository, registroAuditoria);
    }

    @Test
    @DisplayName("Lectura: paciente inexistente se ignora silenciosamente")
    void testLecturaPacienteNoExiste() {
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> accesoService.registrarLecturaDeduplicada(pacienteId, odontologo, "10.0.0.1"));
        verify(accesoRepository, never()).save(any());
        verifyNoInteractions(registroAuditoria);
    }

    @Test
    @DisplayName("Lectura: registra acceso y auditoría LECTURA_EXPEDIENTE; IP nula se guarda como 127.0.0.1")
    void testLecturaRegistra() {
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(paciente));

        accesoService.registrarLecturaDeduplicada(pacienteId, odontologo, null);

        ArgumentCaptor<AccesoHistoria> captor = ArgumentCaptor.forClass(AccesoHistoria.class);
        verify(accesoRepository).save(captor.capture());
        assertEquals(odontologo.id(), captor.getValue().getUsuarioId());
        assertEquals("sede-chilca-1", captor.getValue().getSedeId());
        assertEquals("127.0.0.1", captor.getValue().getIp());

        verify(registroAuditoria).registrarAccesoHistoria(
                eq(odontologo.id()),
                eq("Dr. Pérez"),
                eq("ODONTOLOGO"),
                eq("sede-chilca-1"),
                eq(pacienteId),
                eq("LECTURA_EXPEDIENTE"),
                contains("María Flores"),
                eq("127.0.0.1")
        );
    }

    // ---------------------------------------------------------------- listarAccesos

    @Test
    @DisplayName("Listar: con paciente filtra por paciente; sin paciente devuelve los últimos 200 globales")
    void testListarAccesos() {
        when(accesoRepository.findByPacienteIdOrderByAccedidoEnDesc(pacienteId)).thenReturn(List.of(acceso(odontologo.id(), "Dr. Pérez")));
        when(accesoRepository.findTop200ByOrderByAccedidoEnDesc()).thenReturn(List.of());

        assertEquals(1, accesoService.listarAccesos(pacienteId).size());
        assertTrue(accesoService.listarAccesos(null).isEmpty());

        verify(accesoRepository).findByPacienteIdOrderByAccedidoEnDesc(pacienteId);
        verify(accesoRepository).findTop200ByOrderByAccedidoEnDesc();
    }

    @Test
    @DisplayName("Listar: tolera registros históricos con sede inexistente (e.g. sede-chilca-2) sin fallar")
    void testListarAccesosToleraSedeInexistente() {
        AccesoHistoria historico = AccesoHistoria.builder()
                .id(UUID.randomUUID())
                .pacienteId(pacienteId)
                .usuarioId(odontologo.id())
                .usuarioNombre("Dr. Pérez")
                .usuarioEmail("doc@odontomax.pe")
                .usuarioRol("ODONTOLOGO")
                .sedeId("sede-chilca-2")
                .ip("127.0.0.1")
                .accedidoEn(OffsetDateTime.now())
                .build();
        when(accesoRepository.findByPacienteIdOrderByAccedidoEnDesc(pacienteId)).thenReturn(List.of(historico));

        List<HistoriaClinicaAccesoResponseDTO> res = accesoService.listarAccesos(pacienteId);

        assertEquals(1, res.size());
        assertEquals("sede-chilca-2", res.get(0).getSedeId());
    }
    @Test
    @DisplayName("Registrar apertura: lanza AccessDeniedException cuando odontólogo no tiene cita con el paciente")
    void registrarApertura_OdontologoSinCita_Denegado() {
        doThrow(new AccessDeniedException("Acceso denegado: el paciente no está asignado a este odontólogo."))
                .when(historialCitas).exigirAccesoDeOdontologo(pacienteId);

        assertThrows(AccessDeniedException.class, () -> accesoService.registrarApertura(pacienteId));
    }
}
