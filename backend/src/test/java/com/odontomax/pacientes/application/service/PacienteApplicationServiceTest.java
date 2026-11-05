package com.odontomax.pacientes.application.service;

import com.odontomax.identidad.api.ClinicaUserPrincipal;
import com.odontomax.pacientes.application.dto.request.PacienteRequestDTO;
import com.odontomax.pacientes.application.dto.response.PacienteResponseDTO;
import com.odontomax.pacientes.application.port.out.BorradoArchivosPort;
import com.odontomax.pacientes.application.port.out.ExpedienteDependenciasPort;
import com.odontomax.pacientes.application.port.out.HistorialCitasPort;
import com.odontomax.identidad.api.ClinicaUserPrincipal;
import com.odontomax.pacientes.application.dto.request.PacienteRequestDTO;
import com.odontomax.pacientes.application.dto.response.PacienteResponseDTO;
import com.odontomax.pacientes.application.port.out.BorradoArchivosPort;
import com.odontomax.pacientes.application.port.out.ExpedienteDependenciasPort;
import com.odontomax.pacientes.application.port.out.HistorialCitasPort;
import com.odontomax.pacientes.application.port.out.RegistroAuditoriaPort;
import com.odontomax.pacientes.domain.exception.PacienteConRegistrosProtegidosException;
import com.odontomax.pacientes.domain.model.RegistrosProtegidos;
import com.odontomax.pacientes.domain.exception.PacienteDeudorException;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PacienteApplicationServiceTest {

    @Mock
    private SpringDataPacienteRepository pacienteRepository;

    @Mock
    private com.odontomax.pacientes.api.ConsultaPacientesModuloApi consultaModulo;

    @Mock
    private HistorialCitasPort historialCitas;

    @Mock
    private ExpedienteDependenciasPort expedienteDependencias;

    @Mock
    private BorradoArchivosPort borradoArchivos;

    @Mock
    private RegistroAuditoriaPort registroAuditoria;

    @InjectMocks
    private PacienteApplicationService service;

    private UUID pacienteId;
    private PacienteEntity paciente;

    @BeforeEach
    void setUp() {
        pacienteId = UUID.randomUUID();
        paciente = PacienteEntity.builder()
                .id(pacienteId)
                .dni("45678912")
                .nombres("María")
                .apellidos("Flores")
                .sedeId("sede-chilca-1")
                .saldoPendiente(BigDecimal.ZERO)
                .build();
        autenticar(new ClinicaUserPrincipal(UUID.randomUUID(), "admin@odontomax.pe", "Admin", "ADMIN_SEDE", "sede-chilca-1"));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void autenticar(ClinicaUserPrincipal principal) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + principal.rol()))));
    }

    private PacienteRequestDTO request() {
        return PacienteRequestDTO.builder()
                .dni(" 45678912 ")
                .nombres(" María ")
                .apellidos(" Flores ")
                .telefono("987654321")
                .build();
    }

    // ---------------------------------------------------------------- consultas

    @Test
    @DisplayName("obtenerPorId: paciente inexistente lanza IllegalArgumentException")
    void testObtenerPorIdNoExiste() {
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.obtenerPorId(pacienteId));
        assertEquals("Paciente no encontrado", ex.getMessage());
    }

    @Test
    @DisplayName("obtenerPorId: un usuario de otra sede recibe AccessDenied")
    void testObtenerPorIdOtraSede() {
        autenticar(new ClinicaUserPrincipal(UUID.randomUUID(), "rec@odontomax.pe", "Rec", "RECEPCION", "sede-eltambo"));
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(paciente));

        assertThrows(AccessDeniedException.class, () -> service.obtenerPorId(pacienteId));
    }

    @Test
    @DisplayName("listar: sin búsqueda lista los activos de la sede efectiva y completa la última cita atendida")
    void testListarCompletaUltimaAtencion() {
        when(pacienteRepository.findAllActivos("sede-chilca-1")).thenReturn(List.of(paciente));
        when(historialCitas.ultimasAtenciones(List.of(pacienteId)))
                .thenReturn(Map.of(pacienteId, LocalDate.of(2026, 3, 4)));

        List<PacienteResponseDTO> lista = service.listar(null, null);

        assertEquals(1, lista.size());
        assertEquals(LocalDate.of(2026, 3, 4), lista.get(0).getUltimaCitaAtendida());
        verify(pacienteRepository, never()).searchByTermino(any(), any());
    }

    @Test
    @DisplayName("listarCumpleanerosDeManana: consulta el mes y día de mañana en hora de Lima")
    void testListarCumpleanerosDeManana() {
        LocalDate manana = LocalDate.now(java.time.ZoneId.of("America/Lima")).plusDays(1);
        var resumen = new com.odontomax.pacientes.api.PacienteResumen(pacienteId, "45678912", "María", "Flores", null,
                "sede-chilca-1", null, null, null, 0);
        when(consultaModulo.findCumpleanerosDelDia("sede-chilca-1", manana.getMonthValue(), manana.getDayOfMonth()))
                .thenReturn(List.of(resumen));

        assertEquals(List.of(resumen), service.listarCumpleanerosDeManana("sede-chilca-1"));
    }

    @Test
    @DisplayName("listar: con búsqueda usa el término y no lista todo")
    void testListarConBusqueda() {
        when(pacienteRepository.searchByTermino("flo", "sede-chilca-1")).thenReturn(List.of(paciente));
        when(historialCitas.ultimasAtenciones(any())).thenReturn(Map.of());

        assertEquals(1, service.listar("flo", null).size());
        verify(pacienteRepository, never()).findAllActivos(any());
    }

    @Test
    @DisplayName("listarPaginado: devuelve la misma página con la última cita completada")
    void testListarPaginado() {
        PageRequest pageable = PageRequest.of(0, 20);
        when(pacienteRepository.searchPaginado("flo", "sede-chilca-1", pageable))
                .thenReturn(new PageImpl<>(List.of(paciente), pageable, 1));
        when(historialCitas.ultimasAtenciones(List.of(pacienteId)))
                .thenReturn(Map.of(pacienteId, LocalDate.of(2026, 1, 1)));

        var pagina = service.listarPaginado("flo", null, pageable);

        assertEquals(1, pagina.getTotalElements());
        assertEquals(LocalDate.of(2026, 1, 1), pagina.getContent().get(0).getUltimaCitaAtendida());
    }

    // ---------------------------------------------------------------- guardar (alta y edición)

    @Test
    @DisplayName("registrar: asigna un UUID nuevo, recorta los datos y deja saldo cero y no archivado")
    void testRegistrarAsignaId() {
        when(pacienteRepository.save(any(PacienteEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        PacienteResponseDTO respuesta = service.registrar(request());

        ArgumentCaptor<PacienteEntity> captor = ArgumentCaptor.forClass(PacienteEntity.class);
        verify(pacienteRepository).save(captor.capture());
        assertNotNull(captor.getValue().getId());
        assertEquals("45678912", captor.getValue().getDni());
        assertEquals("María", captor.getValue().getNombres());
        assertEquals("sede-chilca-1", captor.getValue().getSedeId());
        assertEquals(0, BigDecimal.ZERO.compareTo(captor.getValue().getSaldoPendiente()));
        assertFalse(captor.getValue().getArchivado() != null && captor.getValue().getArchivado());
        assertEquals(captor.getValue().getId(), respuesta.getId());
    }

    @Test
    @DisplayName("registrar: ADMIN_GENERAL sin sede recibe el mensaje de sede obligatoria")
    void testRegistrarSinSede() {
        autenticar(new ClinicaUserPrincipal(UUID.randomUUID(), "root@odontomax.pe", "Root", "ADMIN_GENERAL", null));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.registrar(request()));
        assertEquals("Debe especificar una sede válida para registrar al paciente.", ex.getMessage());
        verify(pacienteRepository, never()).save(any());
    }

    @Test
    @DisplayName("actualizar: conserva el ID existente y reemplaza los datos")
    void testActualizarConservaId() {
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(paciente));
        when(pacienteRepository.save(paciente)).thenReturn(paciente);

        PacienteResponseDTO respuesta = service.actualizar(pacienteId, request());

        assertEquals(pacienteId, respuesta.getId());
        assertEquals("987654321", paciente.getTelefono());
    }

    @Test
    @DisplayName("actualizar: paciente inexistente lanza IllegalArgumentException")
    void testActualizarNoExiste() {
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.actualizar(pacienteId, request()));
        verify(pacienteRepository, never()).save(any());
    }

    // ---------------------------------------------------------------- archivar

    @Test
    @DisplayName("Archivar: paciente sin deuda se archiva aunque no indique motivo")
    void testArchivarSinDeudaSinMotivo() {
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(paciente));

        service.archivar(pacienteId, null);

        verify(pacienteRepository).save(paciente);
        assertTrue(paciente.getArchivado() != null && paciente.getArchivado());
        assertNull(paciente.getMotivoArchivo());
    }

    @Test
    @DisplayName("Archivar: paciente con deuda y sin motivo lanza PacienteDeudorException con el saldo")
    void testArchivarConDeudaSinMotivo() {
        paciente.setSaldoPendiente(new BigDecimal("150.00"));
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(paciente));

        PacienteDeudorException ex = assertThrows(PacienteDeudorException.class,
                () -> service.archivar(pacienteId, null));

        assertTrue(ex.getMessage().contains("S/ 150.00"));
        verify(pacienteRepository, never()).save(any());
    }

    @Test
    @DisplayName("Archivar: con deuda, un motivo de menos de 5 caracteres útiles (tras trim) se rechaza")
    void testArchivarConDeudaMotivoCorto() {
        paciente.setSaldoPendiente(new BigDecimal("20"));
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(paciente));

        assertThrows(PacienteDeudorException.class, () -> service.archivar(pacienteId, "   abcd   "));
        verify(pacienteRepository, never()).save(any());
    }

    @Test
    @DisplayName("Archivar: con deuda y motivo detallado se archiva")
    void testArchivarConDeudaMotivoValido() {
        paciente.setSaldoPendiente(new BigDecimal("20"));
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(paciente));

        service.archivar(pacienteId, "Mudanza");

        verify(pacienteRepository).save(paciente);
        assertTrue(paciente.getArchivado() != null && paciente.getArchivado());
        assertEquals("Mudanza", paciente.getMotivoArchivo());
    }

    @Test
    @DisplayName("Archivar: paciente inexistente no llama a archivar")
    void testArchivarNoExiste() {
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.archivar(pacienteId, "Mudanza"));
        verify(pacienteRepository, never()).save(any());
    }

    @Test
    @DisplayName("Archivar: un usuario de otra sede recibe AccessDenied antes de archivar")
    void testArchivarOtraSede() {
        autenticar(new ClinicaUserPrincipal(UUID.randomUUID(), "a@odontomax.pe", "Otro", "ADMIN_SEDE", "sede-eltambo"));
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(paciente));

        assertThrows(AccessDeniedException.class, () -> service.archivar(pacienteId, "Mudanza"));
        verify(pacienteRepository, never()).save(any());
    }

    // ---------------------------------------------------------------- eliminación permanente

    private static final RegistrosProtegidos SIN_PROTEGIDOS = new RegistrosProtegidos(0, 0, 0, 0, 0, 0);

    @Test
    @DisplayName("Eliminar: usuario de otra sede recibe AccessDenied antes de tocar la BD")
    void testEliminarOtraSede() {
        ClinicaUserPrincipal otro = new ClinicaUserPrincipal(UUID.randomUUID(), "rec@odontomax.pe", "Rec", "ADMIN_SEDE", "sede-eltambo");
        autenticar(otro);
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(paciente));

        assertThrows(AccessDeniedException.class, () -> service.eliminar(pacienteId, otro, "10.0.0.1", null));

        verifyNoInteractions(expedienteDependencias, borradoArchivos, registroAuditoria);
        verify(pacienteRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("Eliminar: con notas, antecedentes, accesos o pagos responde 409 y solo permite archivar")
    void testEliminarConRegistrosProtegidos() {
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(paciente));
        when(expedienteDependencias.contarRegistrosProtegidos(pacienteId)).thenReturn(new RegistrosProtegidos(0, 0, 1, 2, 0, 0));

        PacienteConRegistrosProtegidosException ex = assertThrows(PacienteConRegistrosProtegidosException.class,
                () -> service.eliminar(pacienteId, null, "10.0.0.1", null));

        assertTrue(ex.getMessage().contains("notas: 0, antecedentes: 0, accesos: 1, pagos: 2"));
        verify(expedienteDependencias, never()).eliminarDependientes(any());
        verify(pacienteRepository, never()).deleteById(any());
        verifyNoInteractions(borradoArchivos, registroAuditoria);
    }

    @Test
    @DisplayName("Eliminar: sin registros protegidos borra dependientes, el paciente, audita y elimina archivos de R2")
    void testEliminarCasoFeliz() {
        ClinicaUserPrincipal admin = new ClinicaUserPrincipal(UUID.randomUUID(), "admin@odontomax.pe", "Admin", "ADMIN_SEDE", "sede-chilca-1");
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(paciente));
        when(expedienteDependencias.contarRegistrosProtegidos(pacienteId)).thenReturn(SIN_PROTEGIDOS);
        when(expedienteDependencias.archivosRadiograficos(pacienteId)).thenReturn(List.of("r2/rx/rx1.png"));

        service.eliminar(pacienteId, admin, "10.0.0.1", null);

        verify(expedienteDependencias).eliminarDependientes(pacienteId);
        verify(pacienteRepository).deleteById(pacienteId);
        verify(borradoArchivos).borrarTrasCommit(List.of("r2/rx/rx1.png"));
        ArgumentCaptor<String> detalle = ArgumentCaptor.forClass(String.class);
        verify(registroAuditoria).registrarEliminacion(eq(admin), eq(pacienteId), detalle.capture(), eq("10.0.0.1"));
        assertTrue(detalle.getValue().contains("45678912"));
    }

    @Test
    @DisplayName("Eliminar: el motivo escrito queda en la auditoría recortado y sin espacios repetidos")
    void testEliminarRegistraMotivoEnAuditoria() {
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(paciente));
        when(expedienteDependencias.contarRegistrosProtegidos(pacienteId)).thenReturn(SIN_PROTEGIDOS);
        when(expedienteDependencias.archivosRadiograficos(pacienteId)).thenReturn(List.of());

        service.eliminar(pacienteId, null, "10.0.0.1", "  Ficha duplicada /   creada por error  ");

        ArgumentCaptor<String> detalle = ArgumentCaptor.forClass(String.class);
        verify(registroAuditoria).registrarEliminacion(any(), eq(pacienteId), detalle.capture(), any());
        assertTrue(detalle.getValue().contains("Motivo: Ficha duplicada / creada por error"), detalle.getValue());
        assertTrue(detalle.getValue().contains("45678912"), detalle.getValue());
    }

    @Test
    @DisplayName("Eliminar: sin motivo la auditoría dice 'no indicado'")
    void testEliminarSinMotivoLoDejaIndicado() {
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(paciente));
        when(expedienteDependencias.contarRegistrosProtegidos(pacienteId)).thenReturn(SIN_PROTEGIDOS);
        when(expedienteDependencias.archivosRadiograficos(pacienteId)).thenReturn(List.of());

        service.eliminar(pacienteId, null, "10.0.0.1", "   ");

        ArgumentCaptor<String> detalle = ArgumentCaptor.forClass(String.class);
        verify(registroAuditoria).registrarEliminacion(any(), eq(pacienteId), detalle.capture(), any());
        assertTrue(detalle.getValue().contains("Motivo: no indicado"), detalle.getValue());
    }

    @Test
    @DisplayName("Eliminar: un error al consultar radiografías NO se traga: no se borra nada")
    void testEliminarErrorConsultandoRadiografias() {
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(paciente));
        when(expedienteDependencias.contarRegistrosProtegidos(pacienteId)).thenReturn(SIN_PROTEGIDOS);
        when(expedienteDependencias.archivosRadiograficos(pacienteId)).thenThrow(new RuntimeException("timeout"));

        assertThrows(RuntimeException.class, () -> service.eliminar(pacienteId, null, null, null));

        verify(expedienteDependencias, never()).eliminarDependientes(any());
        verify(pacienteRepository, never()).deleteById(any());
        verifyNoInteractions(borradoArchivos, registroAuditoria);
    }

    @Test
    @DisplayName("Eliminar: consentimientos firmados u ordenes de laboratorio son registros protegidos: 409 con mensaje claro y nada se borra")
    void testEliminarConConsentimientosOLaboratorio() {
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(paciente));
        when(expedienteDependencias.contarRegistrosProtegidos(pacienteId))
                .thenReturn(new RegistrosProtegidos(0, 0, 0, 0, 1, 0))
                .thenReturn(new RegistrosProtegidos(0, 0, 0, 0, 0, 1));

        PacienteConRegistrosProtegidosException c = assertThrows(PacienteConRegistrosProtegidosException.class,
                () -> service.eliminar(pacienteId, null, "10.0.0.1", null));
        assertTrue(c.getMessage().contains("consentimientos firmados"), c.getMessage());

        PacienteConRegistrosProtegidosException l = assertThrows(PacienteConRegistrosProtegidosException.class,
                () -> service.eliminar(pacienteId, null, "10.0.0.1", null));
        assertTrue(l.getMessage().contains("órdenes de laboratorio"), l.getMessage());

        verify(expedienteDependencias, never()).eliminarDependientes(any());
        verify(pacienteRepository, never()).deleteById(any());
        verifyNoInteractions(borradoArchivos, registroAuditoria);
    }
    @Test
    @DisplayName("listar: odont�logo consulta solo sus pacientes con cita")
    void testListar_Odontologo_SoloSusPacientes() {
        UUID doctorUserId = UUID.randomUUID();
        autenticar(new ClinicaUserPrincipal(doctorUserId, "doc@odontomax.pe", "Dr. Test", "ODONTOLOGO", "sede-chilca-1"));
        PacienteResponseDTO dto = new PacienteResponseDTO();
        dto.setNombres("Mar�a");
        when(historialCitas.buscarPacientesOdontologo(null, "sede-chilca-1", doctorUserId)).thenReturn(List.of(dto));

        List<PacienteResponseDTO> resultado = service.listar(null, "sede-chilca-1");

        assertEquals(1, resultado.size());
        assertEquals("Mar�a", resultado.get(0).getNombres());
        verify(historialCitas).buscarPacientesOdontologo(null, "sede-chilca-1", doctorUserId);
    }

    @Test
    @DisplayName("listarPaginado: odont�logo consulta solo sus pacientes con cita paginado")
    void testListarPaginado_Odontologo_SoloSusPacientes() {
        UUID doctorUserId = UUID.randomUUID();
        autenticar(new ClinicaUserPrincipal(doctorUserId, "doc@odontomax.pe", "Dr. Test", "ODONTOLOGO", "sede-chilca-1"));
        PageRequest pageable = PageRequest.of(0, 10);
        PacienteResponseDTO dto = new PacienteResponseDTO();
        dto.setNombres("Mar�a");
        when(historialCitas.buscarPacientesOdontologoPaginado(null, "sede-chilca-1", doctorUserId, pageable))
                .thenReturn(new PageImpl<>(List.of(dto), pageable, 1));

        var pagina = service.listarPaginado(null, "sede-chilca-1", pageable);

        assertEquals(1, pagina.getTotalElements());
        verify(historialCitas).buscarPacientesOdontologoPaginado(null, "sede-chilca-1", doctorUserId, pageable);
    }

    @Test
    @DisplayName("obtenerPorId: odont�logo sin cita con el paciente recibe AccessDeniedException")
    void testObtenerPorId_OdontologoSinCita_Denegado() {
        UUID doctorUserId = UUID.randomUUID();
        autenticar(new ClinicaUserPrincipal(doctorUserId, "doc@odontomax.pe", "Dr. Test", "ODONTOLOGO", "sede-chilca-1"));
        doThrow(new AccessDeniedException("Acceso denegado: el paciente no est� asignado a este odont�logo."))
                .when(historialCitas).exigirAccesoDeOdontologo(pacienteId);

        assertThrows(AccessDeniedException.class, () -> service.obtenerPorId(pacienteId));
        verify(historialCitas).exigirAccesoDeOdontologo(pacienteId);
    }

    @Test
    @DisplayName("actualizar: odont�logo sin cita con el paciente recibe AccessDeniedException")
    void testActualizar_OdontologoSinCita_Denegado() {
        UUID doctorUserId = UUID.randomUUID();
        autenticar(new ClinicaUserPrincipal(doctorUserId, "doc@odontomax.pe", "Dr. Test", "ODONTOLOGO", "sede-chilca-1"));
        doThrow(new AccessDeniedException("Acceso denegado: el paciente no est� asignado a este odont�logo."))
                .when(historialCitas).exigirAccesoDeOdontologo(pacienteId);

        assertThrows(AccessDeniedException.class, () -> service.actualizar(pacienteId, request()));
        verify(historialCitas).exigirAccesoDeOdontologo(pacienteId);
    }
}
