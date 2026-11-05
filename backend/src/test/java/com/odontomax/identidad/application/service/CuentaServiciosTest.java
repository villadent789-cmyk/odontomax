package com.odontomax.identidad.application.service;

import com.odontomax.application.dto.request.CambioPasswordRequestDTO;
import com.odontomax.application.dto.request.Desactivar2faRequestDTO;
import com.odontomax.application.dto.request.TotpVerificarRequestDTO;
import com.odontomax.identidad.api.ClinicaUserPrincipal;
import com.odontomax.identidad.application.port.out.BruteForceProtectionPort;
import com.odontomax.identidad.application.port.out.JwtUserCachePort;
import com.odontomax.identidad.application.port.out.PasswordHistorialRepository;
import com.odontomax.identidad.application.port.out.RegistroAuditoriaPort;
import com.odontomax.identidad.application.port.out.TotpPasoUsadoPort;
import com.odontomax.identidad.application.port.out.TotpPort;
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
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * Ramas de error de gestión de cuenta (contraseña y 2FA) que PasswordAndTotpServiceTest no cubre.
 * Reescritura 1:1 de AuthServiceCuentaTest contra los casos de uso y sus puertos.
 */
@ExtendWith(MockitoExtension.class)
class CuentaServiciosTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordHistorialRepository passwordHistorialRepository;

    @Mock
    private RegistroAuditoriaPort registroAuditoria;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TotpPort totpService;

    @Mock
    private JwtUserCachePort jwtUserCache;

    @Mock
    private TotpPasoUsadoPort totpPasoUsado;

    @Mock
    private BruteForceProtectionPort bruteForceProtection;

    @InjectMocks
    private GestionarTotpService gestionarTotpService;

    @InjectMocks
    private CambiarPasswordService cambiarPasswordService;

    private UUID userId;
    private Usuario usuario;
    private ClinicaUserPrincipal doctor;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        usuario = Usuario.builder()
                .id(userId)
                .email("doctor@odontomax.pe")
                .nombre("Dr. Pérez")
                .rol("ODONTOLOGO")
                .sedeId("sede-chilca-1")
                .passwordHash("$2a$10$hashActual")
                .activo(true)
                .build();
        doctor = new ClinicaUserPrincipal(userId, "doctor@odontomax.pe", "Dr. Pérez", "ODONTOLOGO", "sede-chilca-1");
    }

    private static String sha256(String valor) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8)));
    }

    // ---------------------------------------------------------------- 2FA: verificar / desactivar / configurar

    @Test
    @DisplayName("2FA: configurar, verificar y desactivar exigen usuario autenticado")
    void testOperaciones2FASinAutenticar() {
        assertThrows(AccessDeniedException.class, () -> gestionarTotpService.configurar2FA(null, null));
        assertThrows(AccessDeniedException.class, () -> gestionarTotpService.verificarYActivar2FA(new TotpVerificarRequestDTO(123456), null));
        assertThrows(AccessDeniedException.class, () -> gestionarTotpService.desactivar2FA(new Desactivar2faRequestDTO("Actual-1", "123456"), null));
        verifyNoInteractions(usuarioRepository, totpService);
    }

    @Test
    @DisplayName("2FA: verificar sin haber iniciado la configuración (sin secreto) responde 400")
    void testVerificar2FASinSecreto() {
        when(usuarioRepository.findById(userId)).thenReturn(Optional.of(usuario));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> gestionarTotpService.verificarYActivar2FA(new TotpVerificarRequestDTO(123456), doctor));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(totpService, never()).verifyCode(any(), anyInt());
        assertFalse(usuario.isTotpActivado());
    }

    @Test
    @DisplayName("2FA: verificar con código incorrecto no activa el segundo factor")
    void testVerificar2FACodigoIncorrecto() {
        usuario.setTotpSecret("JBSWY3DPEHPK3PXP");
        when(usuarioRepository.findById(userId)).thenReturn(Optional.of(usuario));
        when(totpService.verifyCode("JBSWY3DPEHPK3PXP", 111111)).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> gestionarTotpService.verificarYActivar2FA(new TotpVerificarRequestDTO(111111), doctor));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertFalse(usuario.isTotpActivado());
        verify(usuarioRepository, never()).save(any());
        verify(registroAuditoria, never()).registrarEventoSeguridad(any(), any(), any());
    }

    @Test
    @DisplayName("2FA: verificar con usuario inexistente responde 404")
    void testVerificar2FAUsuarioNoExiste() {
        when(usuarioRepository.findById(userId)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> gestionarTotpService.verificarYActivar2FA(new TotpVerificarRequestDTO(123456), doctor));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    @DisplayName("2FA: desactivar cuando no está activo responde 400 sin validar código")
    void testDesactivar2FANoActivo() {
        when(usuarioRepository.findById(userId)).thenReturn(Optional.of(usuario));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> gestionarTotpService.desactivar2FA(new Desactivar2faRequestDTO("Actual-1", "123456"), doctor));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(totpService, never()).pasoDelCodigo(any(), anyInt());
    }

    @Test
    @DisplayName("2FA: desactivar con código incorrecto conserva secreto y códigos de respaldo")
    void testDesactivar2FACodigoIncorrecto() {
        usuario.setTotpActivado(true);
        usuario.setTotpSecret("JBSWY3DPEHPK3PXP");
        usuario.setTotpBackupCodes("BACKUP01");
        when(usuarioRepository.findById(userId)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("Actual-1", usuario.getPasswordHash())).thenReturn(true);
        when(totpService.pasoDelCodigo("JBSWY3DPEHPK3PXP", 111111)).thenReturn(-1L);

        assertThrows(ResponseStatusException.class,
                () -> gestionarTotpService.desactivar2FA(new Desactivar2faRequestDTO("Actual-1", "111111"), doctor));

        assertTrue(usuario.isTotpActivado());
        assertEquals("JBSWY3DPEHPK3PXP", usuario.getTotpSecret());
        assertEquals("BACKUP01", usuario.getTotpBackupCodes());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("2FA (B-010): reconfigurar con 2FA activo exige código; sin él no cambia nada, con él queda inactivo hasta verificar")
    void testConfigurar2FADesactivaHastaVerificar() {
        usuario.setTotpActivado(true);
        usuario.setTotpSecret("JBSWY3DPEHPK3PXP");
        usuario.setTotpBackupCodes("BACKUP01");
        when(usuarioRepository.findById(userId)).thenReturn(Optional.of(usuario));

        assertThrows(ResponseStatusException.class, () -> gestionarTotpService.configurar2FA(doctor, null));
        assertTrue(usuario.isTotpActivado());
        assertEquals("JBSWY3DPEHPK3PXP", usuario.getTotpSecret());
        verify(usuarioRepository, never()).save(any());
        verify(totpService, never()).generateSecret();

        when(totpService.pasoDelCodigo("JBSWY3DPEHPK3PXP", 123456)).thenReturn(1L);
        when(totpService.generateSecret()).thenReturn("NUEVOSECRETO");
        when(totpService.generateBackupCodes()).thenReturn(List.of("A1", "B2"));

        gestionarTotpService.configurar2FA(doctor, "123456");

        assertFalse(usuario.isTotpActivado());
        assertEquals("NUEVOSECRETO", usuario.getTotpSecret());
        assertEquals("A1,B2", usuario.getTotpBackupCodes());
    }

    // ---------------------------------------------------------------- cambiarPassword

    @Test
    @DisplayName("cambiarPassword: sin usuario autenticado lanza AccessDenied")
    void testCambiarPasswordSinAutenticar() {
        CambioPasswordRequestDTO req = CambioPasswordRequestDTO.builder()
                .passwordActual("Actual123!").passwordNuevo("Nueva2026!#").build();

        assertThrows(AccessDeniedException.class, () -> cambiarPasswordService.cambiarPassword(req, null));
        verifyNoInteractions(usuarioRepository);
    }

    @Test
    @DisplayName("cambiarPassword: la nueva contraseña no puede ser igual a la actual")
    void testCambiarPasswordIgualALaActual() {
        CambioPasswordRequestDTO req = CambioPasswordRequestDTO.builder()
                .passwordActual("Actual123!").passwordNuevo("Actual123!").build();
        when(usuarioRepository.findById(userId)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("Actual123!", "$2a$10$hashActual")).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> cambiarPasswordService.cambiarPassword(req, doctor));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("contraseña actual"));
        verify(passwordHistorialRepository, never()).guardar(any(), any());
        verify(usuarioRepository, never()).save(any());
    }

    // ---------------------------------------------------------------- solicitarRecuperacionPassword

    // ---------------------------------------------------------------- restablecerPasswordConToken

}
