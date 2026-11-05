package com.odontomax.identidad.application.service;

import com.odontomax.application.dto.request.UsuarioCreateRequestDTO;
import com.odontomax.application.dto.request.UsuarioResetPasswordDTO;
import com.odontomax.application.dto.request.UsuarioUpdateRequestDTO;
import com.odontomax.application.dto.response.UsuarioResponseDTO;
import com.odontomax.identidad.api.ClinicaUserPrincipal;
import com.odontomax.identidad.application.port.out.JwtUserCachePort;
import com.odontomax.identidad.application.port.out.PasswordHistorialRepository;
import com.odontomax.identidad.application.port.out.RegistroAuditoriaPort;
import com.odontomax.identidad.application.port.out.UsuarioRepository;
import com.odontomax.identidad.domain.model.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Reescritura 1:1 de UsuarioServiceTest contra puertos. Complementa UsuarioApplicationServiceAutoproteccionTest
 * (autoprotección del administrador en actualizarUsuario). El caso "un fallo al auditar no impide el cambio de
 * estado" está en {@code identidad.infrastructure.adapter.UsuarioAuditoriaFallidaTest}, porque la captura del
 * error vive ahora en el adaptador de auditoría.
 */
@ExtendWith(MockitoExtension.class)
class UsuarioApplicationServiceTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private RegistroAuditoriaPort registroAuditoria;
    @Mock private PasswordHistorialRepository passwordHistorialRepository;
    @Mock private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @Mock
    private JwtUserCachePort jwtUserCache;

    @InjectMocks
    private UsuarioApplicationService usuarioService;

    private final UUID adminId = UUID.randomUUID();
    private final ClinicaUserPrincipal admin =
            new ClinicaUserPrincipal(adminId, "admin@odontomax.pe", "Admin", "ADMIN_GENERAL", null);

    private UUID otroId;
    private Usuario otro;

    @BeforeEach
    void setUp() {
        otroId = UUID.randomUUID();
        otro = Usuario.builder().id(otroId).nombre("Recepción Chilca").email("rec@odontomax.pe")
                .rol("RECEPCION").sedeId("sede-chilca-1").passwordHash("$2a$10$hashActual").activo(true).build();
    }

    private void guardarDevuelveArgumento() {
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            if (u.getId() == null) u.setId(UUID.randomUUID());
            return u;
        });
    }

    private UsuarioCreateRequestDTO.UsuarioCreateRequestDTOBuilder nuevoUsuario() {
        return UsuarioCreateRequestDTO.builder()
                .nombre("  Dra. Ana Ríos  ").email("  Ana.Rios@OdontoMax.pe ").password("ClaveSegura2026!");
    }

    /** Acción y principal de la única llamada a la auditoría de usuarios. */
    private ArgumentCaptor<String> accionAuditada(ArgumentCaptor<ClinicaUserPrincipal> principal) {
        ArgumentCaptor<String> accion = ArgumentCaptor.forClass(String.class);
        verify(registroAuditoria).registrarOperacionUsuarios(principal.capture(), any(), accion.capture(), any(), any(), any(), any());
        return accion;
    }

    // ---------------------------------------------------------------- listar

    @Test
    @DisplayName("Listar: ordena por nombre sin distinguir mayúsculas")
    void testListarOrdenadoPorNombre() {
        when(usuarioRepository.findAll()).thenReturn(List.of(
                Usuario.builder().id(UUID.randomUUID()).nombre("carlos").email("c@x.pe").rol("RECEPCION").build(),
                Usuario.builder().id(UUID.randomUUID()).nombre("Ana").email("a@x.pe").rol("RECEPCION").build(),
                Usuario.builder().id(UUID.randomUUID()).nombre("Beatriz").email("b@x.pe").rol("RECEPCION").build()));

        List<String> nombres = usuarioService.listarTodos().stream().map(UsuarioResponseDTO::getNombre).toList();

        assertEquals(List.of("Ana", "Beatriz", "carlos"), nombres);
    }

    // ---------------------------------------------------------------- crear

    @Test
    @DisplayName("Crear: normaliza correo, nombre y rol; cifra la contraseña, la guarda en historial y audita")
    void testCrearUsuarioExitoso() {
        when(usuarioRepository.findByEmailIgnoreCase("ana.rios@odontomax.pe")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("ClaveSegura2026!")).thenReturn("$2a$10$nuevo");
        guardarDevuelveArgumento();

        UsuarioResponseDTO res = usuarioService.crearUsuario(
                nuevoUsuario().rol(" odontologo ").sedeId(" sede-eltambo ").build(), admin, "10.0.0.1");

        assertEquals("ana.rios@odontomax.pe", res.getEmail());
        assertEquals("Dra. Ana Ríos", res.getNombre());
        assertEquals("ODONTOLOGO", res.getRol());
        assertEquals("sede-eltambo", res.getSedeId());
        assertEquals("sede-eltambo", res.getSedeNombre());
        assertTrue(res.isActivo());

        ArgumentCaptor<UUID> histUsuario = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<String> histHash = ArgumentCaptor.forClass(String.class);
        verify(passwordHistorialRepository).guardar(histUsuario.capture(), histHash.capture());
        assertEquals("$2a$10$nuevo", histHash.getValue());
        assertEquals(res.getId(), histUsuario.getValue());

        ArgumentCaptor<ClinicaUserPrincipal> principal = ArgumentCaptor.forClass(ClinicaUserPrincipal.class);
        ArgumentCaptor<String> accion = accionAuditada(principal);
        assertEquals("CREAR_USUARIO", accion.getValue());
        assertEquals(adminId, principal.getValue().id());
    }

    @Test
    @DisplayName("Crear: correo ya registrado (sin importar mayúsculas) se rechaza")
    void testCrearCorreoDuplicado() {
        when(usuarioRepository.findByEmailIgnoreCase("ana.rios@odontomax.pe")).thenReturn(Optional.of(otro));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                usuarioService.crearUsuario(nuevoUsuario().rol("RECEPCION").sedeId("sede-chilca-1").build(), admin, "10.0.0.1"));

        assertTrue(ex.getMessage().contains("ana.rios@odontomax.pe"));
        verify(usuarioRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    @DisplayName("Crear: ADMIN_GENERAL ignora la sede enviada (acceso a todas las sedes)")
    void testCrearAdminGeneralSinSede() {
        when(usuarioRepository.findByEmailIgnoreCase(any())).thenReturn(Optional.empty());
        guardarDevuelveArgumento();

        UsuarioResponseDTO res = usuarioService.crearUsuario(
                nuevoUsuario().rol("ADMIN_GENERAL").sedeId("sede-chilca-1").build(), admin, "10.0.0.1");

        assertNull(res.getSedeId());
        assertEquals("Todas las Sedes / General", res.getSedeNombre());
    }

    @Test
    @DisplayName("Crear: roles de sede (ADMIN_SEDE, ODONTOLOGO, RECEPCION) exigen sede; en blanco cuenta como ausente")
    void testCrearRolDeSedeSinSede() {
        when(usuarioRepository.findByEmailIgnoreCase(any())).thenReturn(Optional.empty());

        for (String rol : List.of("ADMIN_SEDE", "ODONTOLOGO", "RECEPCION")) {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    usuarioService.crearUsuario(nuevoUsuario().rol(rol).sedeId("   ").build(), admin, "10.0.0.1"));
            assertTrue(ex.getMessage().contains("requiere asignar obligatoriamente una sede"));
        }
        verify(usuarioRepository, never()).save(any());
    }

    // ---------------------------------------------------------------- actualizar

    @Test
    @DisplayName("Actualizar: usuario inexistente lanza IllegalArgumentException")
    void testActualizarNoExiste() {
        when(usuarioRepository.findById(otroId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> usuarioService.actualizarUsuario(otroId,
                UsuarioUpdateRequestDTO.builder().nombre("X").rol("RECEPCION").sedeId("sede-chilca-1").build(), admin, "ip"));
    }

    @Test
    @DisplayName("Actualizar: cambiar a rol de sede sin sede se rechaza")
    void testActualizarRolDeSedeSinSede() {
        when(usuarioRepository.findById(otroId)).thenReturn(Optional.of(otro));

        assertThrows(IllegalArgumentException.class, () -> usuarioService.actualizarUsuario(otroId,
                UsuarioUpdateRequestDTO.builder().nombre("X").rol("ODONTOLOGO").sedeId(null).build(), admin, "ip"));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Actualizar: 'activo' nulo conserva el estado actual del usuario")
    void testActualizarActivoNuloConservaEstado() {
        otro.setActivo(false);
        when(usuarioRepository.findById(otroId)).thenReturn(Optional.of(otro));
        guardarDevuelveArgumento();

        UsuarioResponseDTO res = usuarioService.actualizarUsuario(otroId, UsuarioUpdateRequestDTO.builder()
                .nombre(" Nuevo Nombre ").rol("recepcion").sedeId("sede-villamax-chilca").activo(null).build(), admin, "ip");

        assertFalse(res.isActivo());
        assertEquals("Nuevo Nombre", res.getNombre());
        assertEquals("RECEPCION", res.getRol());
        assertEquals("sede-villamax-chilca", res.getSedeId());
    }

    // ---------------------------------------------------------------- cambiar estado

    @Test
    @DisplayName("Estado: el administrador no puede desactivar su propia cuenta")
    void testCambiarEstadoNoPuedeDesactivarseASiMismo() {
        when(usuarioRepository.findById(adminId)).thenReturn(Optional.of(Usuario.builder()
                .id(adminId).nombre("Admin").email("admin@odontomax.pe").rol("ADMIN_GENERAL").activo(true).build()));

        assertThrows(IllegalArgumentException.class, () -> usuarioService.cambiarEstado(adminId, false, admin, "ip"));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Estado: desactivar a otro usuario audita DESACTIVAR_USUARIO")
    void testCambiarEstadoDesactivaOtro() {
        when(usuarioRepository.findById(otroId)).thenReturn(Optional.of(otro));
        guardarDevuelveArgumento();

        UsuarioResponseDTO res = usuarioService.cambiarEstado(otroId, false, admin, "ip");

        assertFalse(res.isActivo());
        ArgumentCaptor<String> accion = accionAuditada(ArgumentCaptor.forClass(ClinicaUserPrincipal.class));
        assertEquals("DESACTIVAR_USUARIO", accion.getValue());
    }

    // ---------------------------------------------------------------- resetear contraseña

    @Test
    @DisplayName("Resetear: la nueva contraseña no puede ser igual a la actual")
    void testResetearIgualALaActual() {
        when(usuarioRepository.findById(otroId)).thenReturn(Optional.of(otro));
        when(passwordEncoder.matches("ClaveSegura2026!", "$2a$10$hashActual")).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                usuarioService.resetearPassword(otroId, new UsuarioResetPasswordDTO("ClaveSegura2026!"), admin, "ip"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(passwordHistorialRepository, never()).guardar(any(), any());
    }

    @Test
    @DisplayName("Resetear: rechaza una contraseña usada entre las últimas 3")
    void testResetearReutilizaHistorial() {
        when(usuarioRepository.findById(otroId)).thenReturn(Optional.of(otro));
        when(passwordEncoder.matches("ClaveSegura2026!", "$2a$10$hashActual")).thenReturn(false);
        when(passwordHistorialRepository.findUltimosTresHashes(otroId)).thenReturn(List.of("$h1", "$h2"));
        when(passwordEncoder.matches("ClaveSegura2026!", "$h1")).thenReturn(false);
        when(passwordEncoder.matches("ClaveSegura2026!", "$h2")).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                usuarioService.resetearPassword(otroId, new UsuarioResetPasswordDTO("ClaveSegura2026!"), admin, "ip"));

        assertTrue(ex.getReason().contains("últimas 3"));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Resetear: caso feliz guarda la contraseña anterior en historial y cifra la nueva")
    void testResetearExitoso() {
        when(usuarioRepository.findById(otroId)).thenReturn(Optional.of(otro));
        when(passwordEncoder.matches("ClaveSegura2026!", "$2a$10$hashActual")).thenReturn(false);
        when(passwordHistorialRepository.findUltimosTresHashes(otroId)).thenReturn(List.of());
        when(passwordEncoder.encode("ClaveSegura2026!")).thenReturn("$2a$10$nuevo");

        Map<String, Object> res = usuarioService.resetearPassword(otroId, new UsuarioResetPasswordDTO("ClaveSegura2026!"), admin, "ip");

        assertEquals(200, res.get("status"));
        assertEquals("$2a$10$nuevo", otro.getPasswordHash());
        ArgumentCaptor<String> histHash = ArgumentCaptor.forClass(String.class);
        verify(passwordHistorialRepository).guardar(any(), histHash.capture());
        assertEquals("$2a$10$hashActual", histHash.getValue());
        verify(usuarioRepository).save(otro);
    }

    // ---------------------------------------------------------------- eliminar

    @Test
    @DisplayName("Eliminar: el administrador no puede eliminar su propia cuenta")
    void testEliminarPropiaCuenta() {
        when(usuarioRepository.findById(adminId)).thenReturn(Optional.of(Usuario.builder()
                .id(adminId).nombre("Admin").email("admin@odontomax.pe").rol("ADMIN_GENERAL").build()));

        assertThrows(IllegalArgumentException.class, () -> usuarioService.eliminarUsuario(adminId, admin, "ip"));
        verify(usuarioRepository, never()).eliminar(any());
    }

    @Test
    @DisplayName("Eliminar: usuario sin registros vinculados se borra físicamente")
    void testEliminarFisico() {
        when(usuarioRepository.findById(otroId)).thenReturn(Optional.of(otro));
        when(usuarioRepository.tieneRegistrosVinculados(otroId)).thenReturn(false);

        Map<String, Object> res = usuarioService.eliminarUsuario(otroId, admin, "ip");

        assertEquals("FISICO", res.get("tipo"));
        // eliminar = DELETE + flush inmediato en el adaptador (ver UsuarioJpaAdapterTest)
        verify(usuarioRepository).eliminar(otroId);
        verify(usuarioRepository, never()).save(any());
        ArgumentCaptor<String> accion = accionAuditada(ArgumentCaptor.forClass(ClinicaUserPrincipal.class));
        assertEquals("ELIMINAR_USUARIO_FISICO", accion.getValue());
    }

    @Test
    @DisplayName("Eliminar: con registros vinculados decide la baja lógica ANTES de intentar el borrado (sin DELETE ni flush)")
    void testEliminarConRegistrosVinculadosHaceBajaLogica() {
        when(usuarioRepository.findById(otroId)).thenReturn(Optional.of(otro));
        when(usuarioRepository.tieneRegistrosVinculados(otroId)).thenReturn(true);

        Map<String, Object> res = usuarioService.eliminarUsuario(otroId, admin, "ip");

        assertEquals("LOGICO", res.get("tipo"));
        assertFalse(otro.isActivo());
        verify(usuarioRepository, never()).eliminar(any());
        verify(usuarioRepository).save(otro);
        ArgumentCaptor<String> accion = accionAuditada(ArgumentCaptor.forClass(ClinicaUserPrincipal.class));
        assertEquals("BAJA_USUARIO_LOGICO", accion.getValue());
    }

    @Test
    @DisplayName("Eliminar: una violación de integridad inesperada no se oculta como éxito (la transacción ya quedó marcada para rollback)")
    void testEliminarViolacionInesperadaSePropaga() {
        when(usuarioRepository.findById(otroId)).thenReturn(Optional.of(otro));
        when(usuarioRepository.tieneRegistrosVinculados(otroId)).thenReturn(false);
        doThrow(new DataIntegrityViolationException("fk_nueva_tabla")).when(usuarioRepository).eliminar(otroId);

        assertThrows(DataIntegrityViolationException.class, () -> usuarioService.eliminarUsuario(otroId, admin, "ip"));

        verify(usuarioRepository, never()).save(any());
        verifyNoInteractions(registroAuditoria);
    }
    // ------------------------------------------------ sincronización con el perfil de doctor

    @Test
    @DisplayName("Crear ODONTOLOGO: publica evento para crear/vincular doctor; otros roles no")
    void testCrearOdontologoCreaPerfilDeDoctor() {
        when(usuarioRepository.findByEmailIgnoreCase(any())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("$2a$10$nuevo");
        guardarDevuelveArgumento();

        usuarioService.crearUsuario(nuevoUsuario().rol("ODONTOLOGO").sedeId("sede-chilca-1").build(), admin, "ip");
        ArgumentCaptor<com.odontomax.identidad.api.event.UsuarioOdontologoRegistrado> eventCaptor =
                ArgumentCaptor.forClass(com.odontomax.identidad.api.event.UsuarioOdontologoRegistrado.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertEquals("sede-chilca-1", eventCaptor.getValue().sedeId());

        clearInvocations(eventPublisher);
        usuarioService.crearUsuario(nuevoUsuario().email("rec@x.pe").rol("RECEPCION").sedeId("sede-chilca-1").build(), admin, "ip");
        verify(eventPublisher, never()).publishEvent(any(com.odontomax.identidad.api.event.UsuarioOdontologoRegistrado.class));
    }

    @Test
    @DisplayName("Cambiar estado: desactivar/reactivar la cuenta publica evento de sincronizacion")
    void testCambiarEstadoSincronizaDoctor() {
        when(usuarioRepository.findById(otroId)).thenReturn(Optional.of(otro));
        guardarDevuelveArgumento();

        usuarioService.cambiarEstado(otroId, false, admin, "ip");
        verify(eventPublisher).publishEvent(new com.odontomax.identidad.api.event.UsuarioEstadoModificado(otroId, false));

        usuarioService.cambiarEstado(otroId, true, admin, "ip");
        verify(eventPublisher).publishEvent(new com.odontomax.identidad.api.event.UsuarioEstadoModificado(otroId, true));
    }
}
