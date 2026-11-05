package com.odontomax.identidad.application.service;

import com.odontomax.application.dto.request.Desactivar2faRequestDTO;
import com.odontomax.application.dto.request.CambioPasswordRequestDTO;
import com.odontomax.application.dto.request.TotpLoginRequestDTO;
import com.odontomax.application.dto.request.TotpVerificarRequestDTO;
import com.odontomax.application.dto.response.LoginResponseDTO;
import com.odontomax.application.dto.response.TotpConfigurarResponseDTO;
import com.odontomax.identidad.api.ClinicaUserPrincipal;
import com.odontomax.identidad.application.port.out.JwtUserCachePort;
import com.odontomax.identidad.application.port.out.BruteForceProtectionPort;
import com.odontomax.identidad.application.port.out.Login2faPendientePort;
import com.odontomax.identidad.application.port.out.PasswordHistorialRepository;
import com.odontomax.identidad.application.port.out.RegistroAuditoriaPort;
import com.odontomax.identidad.application.port.out.TokenJwtPort;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Reescritura 1:1 del PasswordAndTotpServiceTest de AuthService contra los casos de uso y sus puertos.
 * Las aserciones de cifrado del re-cifrado 2FA (antes sobre el UPDATE nativo) están en UsuarioJpaAdapterTest.
 */
@ExtendWith(MockitoExtension.class)
class PasswordAndTotpServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordHistorialRepository passwordHistorialRepository;

    @Mock
    private RegistroAuditoriaPort registroAuditoria;

    @Mock
    private TokenJwtPort jwtTokenProvider;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TotpPort totpService;

    @Mock
    private BruteForceProtectionPort bruteForceProtection;

    @Mock
    private Login2faPendientePort login2faPendiente;

    @Mock
    private JwtUserCachePort jwtUserCache;

    @Mock
    private TotpPasoUsadoPort totpPasoUsado;

    @InjectMocks
    private CambiarPasswordService cambiarPasswordService;

    @InjectMocks
    private GestionarTotpService gestionarTotpService;

    @InjectMocks
    private LoginService loginService;

    private UUID userId;
    private Usuario userEntity;
    private ClinicaUserPrincipal userPrincipal;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        userEntity = Usuario.builder()
                .id(userId)
                .email("admin@odontomax.pe")
                .nombre("Administrador")
                .rol("ADMIN_GENERAL")
                .passwordHash("$2a$10$hashActual")
                .activo(true)
                .totpActivado(false)
                .build();

        userPrincipal = new ClinicaUserPrincipal(
                userId, "admin@odontomax.pe", "Administrador", "ADMIN_GENERAL", null
        );
    }

    private void stubTokenTemporal() {
        when(jwtTokenProvider.validateToken("temp-tok")).thenReturn(true);
        when(jwtTokenProvider.leerClaims("temp-tok")).thenReturn(new TokenJwtPort.TokenClaims("admin@odontomax.pe", "2FA_PENDING", 0));
    }

    @Test
    @DisplayName("cambiarPassword: falla si la contraseña actual no coincide")
    void cambiarPassword_claveActualIncorrecta_lanzaExcepcion() {
        CambioPasswordRequestDTO req = CambioPasswordRequestDTO.builder()
                .passwordActual("Erronea123!")
                .passwordNuevo("NuevaClave2026!")
                .build();

        when(usuarioRepository.findById(userId)).thenReturn(Optional.of(userEntity));
        when(passwordEncoder.matches("Erronea123!", "$2a$10$hashActual")).thenReturn(false);

        assertThatThrownBy(() -> cambiarPasswordService.cambiarPassword(req, userPrincipal))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("La contraseña actual es incorrecta");
    }

    @Test
    @DisplayName("cambiarPassword: falla si la nueva contraseña coincide con alguna de las últimas 3")
    void cambiarPassword_reutilizaUltimas3_lanza400() {
        CambioPasswordRequestDTO req = CambioPasswordRequestDTO.builder()
                .passwordActual("Correcta123!")
                .passwordNuevo("ClaveHistorica1!")
                .build();

        when(usuarioRepository.findById(userId)).thenReturn(Optional.of(userEntity));
        when(passwordEncoder.matches("Correcta123!", "$2a$10$hashActual")).thenReturn(true);
        when(passwordEncoder.matches("ClaveHistorica1!", "$2a$10$hashActual")).thenReturn(false);

        when(passwordHistorialRepository.findUltimosTresHashes(userId))
                .thenReturn(List.of("$2a$10$hashHist1"));
        when(passwordEncoder.matches("ClaveHistorica1!", "$2a$10$hashHist1")).thenReturn(true);

        assertThatThrownBy(() -> cambiarPasswordService.cambiarPassword(req, userPrincipal))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("no puede coincidir con ninguna de las últimas 3 contraseñas");
    }

    @Test
    @DisplayName("cambiarPassword: caso feliz guarda historial y actualiza contraseña")
    void cambiarPassword_casoFeliz_actualizaYGuardaHistorial() {
        CambioPasswordRequestDTO req = CambioPasswordRequestDTO.builder()
                .passwordActual("Correcta123!")
                .passwordNuevo("NuevaClave2026!")
                .build();

        when(usuarioRepository.findById(userId)).thenReturn(Optional.of(userEntity));
        when(passwordEncoder.matches("Correcta123!", "$2a$10$hashActual")).thenReturn(true);
        when(passwordEncoder.matches("NuevaClave2026!", "$2a$10$hashActual")).thenReturn(false);
        when(passwordHistorialRepository.findUltimosTresHashes(userId))
                .thenReturn(Collections.emptyList());
        when(passwordEncoder.encode("NuevaClave2026!")).thenReturn("$2a$10$nuevoHash");

        Map<String, String> res = cambiarPasswordService.cambiarPassword(req, userPrincipal);

        assertThat(res).containsEntry("mensaje", "Contraseña actualizada exitosamente.");
        verify(passwordHistorialRepository).guardar(userId, "$2a$10$hashActual");
        verify(usuarioRepository).save(userEntity);
    }

    @Test
    @DisplayName("2FA TOTP: configurar2FA genera secreto, QR y códigos de respaldo")
    void configurar2FA_adminGeneral_generaSecretYBackupCodes() {
        when(usuarioRepository.findById(userId)).thenReturn(Optional.of(userEntity));
        when(totpService.generateSecret()).thenReturn("JBSWY3DPEHPK3PXP");
        when(totpService.generateBackupCodes()).thenReturn(List.of("CODE1234", "CODE5678", "CODE9012", "CODE3456", "CODE7890"));
        when(totpService.getOtpAuthUrl(any(), any())).thenReturn("otpauth://totp/OdontoMax:admin@odontomax.pe?secret=JBSWY3DPEHPK3PXP");
        when(totpService.generateQrCodeDataUri(any(), eq(250), eq(250))).thenReturn("data:image/png;base64,mockQrData");

        TotpConfigurarResponseDTO res = gestionarTotpService.configurar2FA(userPrincipal, null);

        assertThat(res.getSecret()).isEqualTo("JBSWY3DPEHPK3PXP");
        assertThat(res.getQrCode()).isEqualTo("data:image/png;base64,mockQrData");
        assertThat(res.getBackupCodes()).hasSize(5);
        verify(usuarioRepository).save(userEntity);
    }

    @Test
    @DisplayName("2FA TOTP: configurar2FA permite enrolamiento a otros roles (opcional)")
    void configurar2FA_otrosRoles_permiteEnrolamiento() {
        ClinicaUserPrincipal docPrincipal = new ClinicaUserPrincipal(
                userId, "doc@odontomax.pe", "Dr. Pérez", "ODONTOLOGO", "sede-chilca-1"
        );
        userEntity.setRol("ODONTOLOGO");
        when(usuarioRepository.findById(userId)).thenReturn(Optional.of(userEntity));
        when(totpService.generateSecret()).thenReturn("JBSWY3DPEHPK3PXP");
        when(totpService.generateBackupCodes()).thenReturn(List.of("CODE1234", "CODE5678", "CODE9012", "CODE3456", "CODE7890"));
        when(totpService.getOtpAuthUrl(any(), any())).thenReturn("otpauth://totp/OdontoMax:doc@odontomax.pe?secret=JBSWY3DPEHPK3PXP");
        when(totpService.generateQrCodeDataUri(any(), eq(250), eq(250))).thenReturn("data:image/png;base64,mockQrData");

        TotpConfigurarResponseDTO res = gestionarTotpService.configurar2FA(docPrincipal, null);

        assertThat(res.getSecret()).isEqualTo("JBSWY3DPEHPK3PXP");
        assertThat(res.getQrCode()).isNotBlank();
        verify(usuarioRepository).save(userEntity);
    }

    @Test
    @DisplayName("2FA TOTP: desactivar2FA prohibido para ADMIN_GENERAL por política institucional")
    void desactivar2FA_adminGeneral_lanzaAccessDenied() {
        assertThatThrownBy(() -> gestionarTotpService.desactivar2FA(new Desactivar2faRequestDTO("Actual-1", "123456"), userPrincipal))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("2FA obligatorio");
    }

    @Test
    @DisplayName("2FA TOTP: desactivar2FA permitido para otros roles con contraseña actual y código TOTP válidos")
    void desactivar2FA_otrosRoles_permiteDesactivacion() {
        ClinicaUserPrincipal docPrincipal = new ClinicaUserPrincipal(
                userId, "doc@odontomax.pe", "Dr. Pérez", "ODONTOLOGO", "sede-chilca-1"
        );
        userEntity.setRol("ODONTOLOGO");
        userEntity.setTotpActivado(true);
        userEntity.setTotpSecret("JBSWY3DPEHPK3PXP");
        userEntity.setTotpBackupCodes("BACKUP01");

        when(usuarioRepository.findById(userId)).thenReturn(Optional.of(userEntity));
        when(passwordEncoder.matches("Actual-1", "$2a$10$hashActual")).thenReturn(true);
        when(totpService.pasoDelCodigo("JBSWY3DPEHPK3PXP", 654321)).thenReturn(1L);

        Map<String, Object> res = gestionarTotpService.desactivar2FA(new Desactivar2faRequestDTO("Actual-1", "654321"), docPrincipal);

        assertThat(res).containsEntry("activo", false);
        assertThat(userEntity.isTotpActivado()).isFalse();
        assertThat(userEntity.getTotpSecret()).isNull();
        assertThat(userEntity.getTotpBackupCodes()).isNull();
        verify(usuarioRepository).save(userEntity);
    }

    @Test
    @DisplayName("2FA TOTP: verificarYActivar2FA activa la bandera si el código es correcto")
    void verificarYActivar2FA_codigoValido_activa2FA() {
        userEntity.setTotpSecret("JBSWY3DPEHPK3PXP");
        when(usuarioRepository.findById(userId)).thenReturn(Optional.of(userEntity));
        when(totpService.verifyCode("JBSWY3DPEHPK3PXP", 123456)).thenReturn(true);

        Map<String, Object> res = gestionarTotpService.verificarYActivar2FA(new TotpVerificarRequestDTO(123456), userPrincipal);

        assertThat(res).containsEntry("activo", true);
        assertThat(userEntity.isTotpActivado()).isTrue();
        verify(usuarioRepository).save(userEntity);
    }

    @Test
    @DisplayName("2FA TOTP: completarLogin2FA acepta código de respaldo válido")
    void completarLogin2FA_codigoRespaldoValido_emiteTokenFinal() {
        userEntity.setTotpActivado(true);
        userEntity.setTotpBackupCodes("BACKUP01,BACKUP02");

        stubTokenTemporal();

        when(usuarioRepository.findByEmailIgnoreCase("admin@odontomax.pe")).thenReturn(Optional.of(userEntity));
        when(jwtTokenProvider.generateToken(any(), any(), any(), any(), any(), anyInt())).thenReturn("final-jwt-token");
        when(jwtTokenProvider.getExpirationMs()).thenReturn(28800000L);

        TotpLoginRequestDTO req = TotpLoginRequestDTO.builder()
                .tempToken("temp-tok")
                .codigo("BACKUP01")
                .build();

        LoginResponseDTO res = loginService.completarLogin2FA(req, "127.0.0.1");

        assertThat(res.getToken()).isEqualTo("final-jwt-token");
        assertThat(userEntity.getTotpBackupCodes()).isEqualTo("BACKUP02"); // consumió BACKUP01
        verify(usuarioRepository).save(userEntity);
    }

    @Test
    @DisplayName("2FA TOTP: completarLogin2FA re-cifra con la clave activa mediante UPDATE explícito si se leyó con la clave anterior")
    void completarLogin2FA_reencryptionNeeded_persisteConClaveActiva() {
        userEntity.setTotpActivado(true);
        userEntity.setTotpSecret("JBSWY3DPEHPK3PXP");
        userEntity.setTotpBackupCodes("BACKUP01,BACKUP02");
        userEntity.setRecifradoTotpPendiente(true); // simula @PostLoad tras descifrar con TOTP_ENCRYPTION_KEY_OLD

        stubTokenTemporal();
        when(usuarioRepository.findByEmailIgnoreCase("admin@odontomax.pe")).thenReturn(Optional.of(userEntity));
        when(totpService.pasoDelCodigo("JBSWY3DPEHPK3PXP", 123456)).thenReturn(1L);
        when(jwtTokenProvider.generateToken(any(), any(), any(), any(), any(), anyInt())).thenReturn("final-jwt-token");

        loginService.completarLogin2FA(TotpLoginRequestDTO.builder().tempToken("temp-tok").codigo("123456").build(), "127.0.0.1");

        // El UPDATE explícito con la clave activa lo hace el adaptador (ver UsuarioJpaAdapterTest)
        verify(usuarioRepository).recifrarCredencialesTotp(userEntity);
        // save() no emitiría UPDATE (texto plano sin cambios), por eso no debe ser el mecanismo usado
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("2FA TOTP: completarLogin2FA no re-cifra si los datos ya usan la clave activa")
    void completarLogin2FA_sinReencryption_noEjecutaUpdate() {
        userEntity.setTotpActivado(true);
        userEntity.setTotpSecret("JBSWY3DPEHPK3PXP");

        stubTokenTemporal();
        when(usuarioRepository.findByEmailIgnoreCase("admin@odontomax.pe")).thenReturn(Optional.of(userEntity));
        when(totpService.pasoDelCodigo("JBSWY3DPEHPK3PXP", 123456)).thenReturn(1L);
        when(jwtTokenProvider.generateToken(any(), any(), any(), any(), any(), anyInt())).thenReturn("final-jwt-token");

        loginService.completarLogin2FA(TotpLoginRequestDTO.builder().tempToken("temp-tok").codigo("123456").build(), "127.0.0.1");

        verify(usuarioRepository, never()).recifrarCredencialesTotp(any());
    }
}
