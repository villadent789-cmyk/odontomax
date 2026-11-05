package com.odontomax.identidad.application.service;

import com.odontomax.application.dto.request.LoginRequestDTO;
import com.odontomax.application.dto.request.TotpLoginRequestDTO;
import com.odontomax.application.dto.response.LoginResponseDTO;
import com.odontomax.application.dto.response.UsuarioDTO;
import com.odontomax.identidad.application.port.out.BruteForceProtectionPort;
import com.odontomax.identidad.application.port.out.Login2faPendientePort;
import com.odontomax.identidad.application.port.out.TokenJwtPort;
import com.odontomax.identidad.application.port.out.TotpPasoUsadoPort;
import com.odontomax.identidad.application.port.out.TotpPort;
import com.odontomax.identidad.application.port.out.UsuarioRepository;
import com.odontomax.identidad.domain.model.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Reescritura 1:1 de AuthServiceTest (login, completarLogin2FA y perfil) contra puertos.
 * Los 8 casos de bloqueo por fuerza bruta están en
 * {@code identidad.infrastructure.adapter.LoginBruteForceTest}, con el contador real en memoria.
 */
@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

    private static final String EMAIL = "doctor@odontomax.pe";
    private static final String HASH = "$2a$10$hashActual";
    private static final String IP = "10.0.0.1";

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private TokenJwtPort tokenJwtPort;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private BruteForceProtectionPort bruteForceProtection;

    @Mock
    private TotpPort totpPort;

    @Mock
    private Login2faPendientePort login2faPendiente;

    @Mock
    private TotpPasoUsadoPort totpPasoUsado;

    @InjectMocks
    private LoginService loginService;

    @InjectMocks
    private ConsultarPerfilService consultarPerfilService;

    private UUID userId;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        usuario = Usuario.builder()
                .id(userId)
                .email(EMAIL)
                .nombre("Dr. Pérez")
                .rol("ODONTOLOGO")
                .sedeId("sede-chilca-1")
                .passwordHash(HASH)
                .activo(true)
                .totpActivado(false)
                .build();
    }

    private LoginRequestDTO credenciales(String email, String password) {
        return LoginRequestDTO.builder().email(email).password(password).build();
    }

    private void stubTokenTemporal(String token, String tipo, String subject) {
        when(tokenJwtPort.validateToken(token)).thenReturn(true);
        when(tokenJwtPort.leerClaims(token)).thenReturn(new TokenJwtPort.TokenClaims(subject, tipo, 0));
    }

    // ---------------------------------------------------------------- login

    @Test
    @DisplayName("Login: caso feliz emite JWT Bearer, normaliza el correo y actualiza el último acceso")
    void testLoginExitoso() {
        when(usuarioRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("Correcta123!", HASH)).thenReturn(true);
        when(tokenJwtPort.generateToken(userId, EMAIL, "Dr. Pérez", "ODONTOLOGO", "sede-chilca-1", 0))
                .thenReturn("jwt-final");
        when(tokenJwtPort.getExpirationMs()).thenReturn(28_800_000L);

        LoginResponseDTO res = loginService.login(credenciales("  Doctor@OdontoMax.pe ", "Correcta123!"), IP);

        assertEquals("jwt-final", res.getToken());
        assertEquals("Bearer", res.getTokenTipo());
        assertEquals(28_800_000L, res.getExpiracionMs());
        assertEquals(userId, res.getUsuario().getId());
        assertEquals("sede-chilca-1", res.getUsuario().getSedeId());
        assertNotNull(usuario.getUltimoAcceso());
        verify(usuarioRepository).save(usuario);
        verify(tokenJwtPort, never()).generateTemp2FaToken(any(), any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("Login: usuario con 2FA activo recibe token temporal 2FA_PENDING de 5 minutos, no el JWT final")
    void testLoginCon2FAEmiteTokenTemporal() {
        usuario.setTotpActivado(true);
        when(usuarioRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("Correcta123!", HASH)).thenReturn(true);
        when(tokenJwtPort.generateTemp2FaToken(userId, EMAIL, "Dr. Pérez", "ODONTOLOGO", 0)).thenReturn("temp-2fa");

        LoginResponseDTO res = loginService.login(credenciales(EMAIL, "Correcta123!"), IP);

        assertEquals("temp-2fa", res.getToken());
        assertEquals("2FA_PENDING", res.getTokenTipo());
        assertEquals(300_000L, res.getExpiracionMs());
        verify(tokenJwtPort, never()).generateToken(any(), any(), any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("Login: correo inexistente responde el mismo mensaje genérico que una contraseña errónea")
    void testLoginCorreoInexistente() {
        when(usuarioRepository.findByEmailIgnoreCase("nadie@odontomax.pe")).thenReturn(Optional.empty());

        BadCredentialsException ex = assertThrows(BadCredentialsException.class,
                () -> loginService.login(credenciales("nadie@odontomax.pe", "Lo-que-sea1!"), IP));

        assertEquals("Credenciales inválidas. Correo o contraseña incorrectos.", ex.getMessage());
        verify(passwordEncoder, never()).matches(any(), any());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Login: cuenta inactiva se rechaza aunque la contraseña sea correcta")
    void testLoginCuentaInactiva() {
        usuario.setActivo(false);
        when(usuarioRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(usuario));

        BadCredentialsException ex = assertThrows(BadCredentialsException.class,
                () -> loginService.login(credenciales(EMAIL, "Correcta123!"), IP));

        assertEquals("Credenciales inválidas. Correo o contraseña incorrectos.", ex.getMessage());
        verify(passwordEncoder, never()).matches(any(), any());
        verify(tokenJwtPort, never()).generateToken(any(), any(), any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("Login: contraseña incorrecta lanza BadCredentials y no emite token")
    void testLoginPasswordIncorrecta() {
        when(usuarioRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("Erronea123!", HASH)).thenReturn(false);

        assertThrows(BadCredentialsException.class,
                () -> loginService.login(credenciales(EMAIL, "Erronea123!"), IP));

        verify(usuarioRepository, never()).save(any());
        verify(tokenJwtPort, never()).generateToken(any(), any(), any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("Login: usuario sin hash de contraseña (cuenta solo externa) no puede iniciar sesión")
    void testLoginSinPasswordHash() {
        usuario.setPasswordHash(null);
        when(usuarioRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(usuario));

        assertThrows(BadCredentialsException.class,
                () -> loginService.login(credenciales(EMAIL, "Correcta123!"), IP));

        verify(passwordEncoder, never()).matches(any(), any());
    }

    // ---------------------------------------------------------------- completarLogin2FA

    @Test
    @DisplayName("2FA: token temporal inválido o expirado se rechaza sin leer claims")
    void testCompletar2FATokenInvalido() {
        when(tokenJwtPort.validateToken("temp-tok")).thenReturn(false);

        BadCredentialsException ex = assertThrows(BadCredentialsException.class, () ->
                loginService.completarLogin2FA(TotpLoginRequestDTO.builder().tempToken("temp-tok").codigo("123456").build(), "127.0.0.1"));

        assertTrue(ex.getMessage().contains("Token temporal inválido"));
        verify(tokenJwtPort, never()).leerClaims(any());
    }

    @Test
    @DisplayName("2FA: un JWT de sesión normal no sirve como token temporal de segundo factor")
    void testCompletar2FAConTokenDeSesionNormal() {
        stubTokenTemporal("jwt-normal", null, EMAIL);

        BadCredentialsException ex = assertThrows(BadCredentialsException.class, () ->
                loginService.completarLogin2FA(TotpLoginRequestDTO.builder().tempToken("jwt-normal").codigo("123456").build(), "127.0.0.1"));

        assertTrue(ex.getMessage().contains("no es un token temporal"));
        verify(usuarioRepository, never()).findByEmailIgnoreCase(any());
    }

    @Test
    @DisplayName("2FA: usuario desactivado entre el login y el segundo factor no obtiene token")
    void testCompletar2FAUsuarioInactivo() {
        usuario.setActivo(false);
        stubTokenTemporal("temp-tok", "2FA_PENDING", EMAIL);
        when(usuarioRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(usuario));

        assertThrows(BadCredentialsException.class, () ->
                loginService.completarLogin2FA(TotpLoginRequestDTO.builder().tempToken("temp-tok").codigo("123456").build(), "127.0.0.1"));

        verify(tokenJwtPort, never()).generateToken(any(), any(), any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("2FA: código TOTP válido emite JWT final y no consume códigos de respaldo")
    void testCompletar2FACodigoTotpValido() {
        usuario.setTotpActivado(true);
        usuario.setTotpSecret("JBSWY3DPEHPK3PXP");
        usuario.setTotpBackupCodes("BACKUP01,BACKUP02");
        stubTokenTemporal("temp-tok", "2FA_PENDING", EMAIL);
        when(usuarioRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(usuario));
        when(totpPort.pasoDelCodigo("JBSWY3DPEHPK3PXP", 123456)).thenReturn(1L);
        when(tokenJwtPort.generateToken(userId, EMAIL, "Dr. Pérez", "ODONTOLOGO", "sede-chilca-1", 0)).thenReturn("jwt-final");
        when(tokenJwtPort.getExpirationMs()).thenReturn(28_800_000L);

        LoginResponseDTO res = loginService.completarLogin2FA(
                TotpLoginRequestDTO.builder().tempToken("temp-tok").codigo(" 123456 ").build(), "127.0.0.1");

        assertEquals("jwt-final", res.getToken());
        assertEquals("Bearer", res.getTokenTipo());
        assertEquals("BACKUP01,BACKUP02", usuario.getTotpBackupCodes());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("2FA: código de respaldo se acepta sin distinguir mayúsculas y solo una vez")
    void testCompletar2FACodigoRespaldoUnSoloUso() {
        usuario.setTotpActivado(true);
        usuario.setTotpBackupCodes("ABCD1234,EFGH5678");
        stubTokenTemporal("temp-tok", "2FA_PENDING", EMAIL);
        when(usuarioRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(usuario));
        when(tokenJwtPort.generateToken(any(), any(), any(), any(), any(), anyInt())).thenReturn("jwt-final");

        loginService.completarLogin2FA(TotpLoginRequestDTO.builder().tempToken("temp-tok").codigo("abcd1234").build(), "127.0.0.1");
        assertEquals("EFGH5678", usuario.getTotpBackupCodes());

        // Reutilizar el mismo código de respaldo debe fallar
        assertThrows(BadCredentialsException.class, () ->
                loginService.completarLogin2FA(TotpLoginRequestDTO.builder().tempToken("temp-tok").codigo("ABCD1234").build(), "127.0.0.1"));
        verify(totpPort, never()).pasoDelCodigo(any(), anyInt());
    }

    @Test
    @DisplayName("2FA: código TOTP incorrecto y sin códigos de respaldo lanza BadCredentials")
    void testCompletar2FACodigoInvalido() {
        usuario.setTotpActivado(true);
        usuario.setTotpSecret("JBSWY3DPEHPK3PXP");
        stubTokenTemporal("temp-tok", "2FA_PENDING", EMAIL);
        when(usuarioRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(usuario));
        when(totpPort.pasoDelCodigo("JBSWY3DPEHPK3PXP", 999999)).thenReturn(-1L);

        BadCredentialsException ex = assertThrows(BadCredentialsException.class, () ->
                loginService.completarLogin2FA(TotpLoginRequestDTO.builder().tempToken("temp-tok").codigo("999999").build(), "127.0.0.1"));

        assertTrue(ex.getMessage().contains("2FA"));
        verify(tokenJwtPort, never()).generateToken(any(), any(), any(), any(), any(), anyInt());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("2FA: un código que no es de 6 dígitos no se evalúa como TOTP")
    void testCompletar2FACodigoNoNumericoNoVaATotp() {
        usuario.setTotpActivado(true);
        usuario.setTotpSecret("JBSWY3DPEHPK3PXP");
        usuario.setTotpBackupCodes("BACKUP01");
        stubTokenTemporal("temp-tok", "2FA_PENDING", EMAIL);
        when(usuarioRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(usuario));

        assertThrows(BadCredentialsException.class, () ->
                loginService.completarLogin2FA(TotpLoginRequestDTO.builder().tempToken("temp-tok").codigo("12345").build(), "127.0.0.1"));

        verify(totpPort, never()).pasoDelCodigo(anyString(), anyInt());
        assertEquals("BACKUP01", usuario.getTotpBackupCodes());
    }

    // ---------------------------------------------------------------- getPerfilFromToken

    @Test
    @DisplayName("Perfil: acepta el header 'Bearer ...' y devuelve los datos del usuario activo")
    void testPerfilDesdeTokenValido() {
        when(tokenJwtPort.validateToken("jwt-ok")).thenReturn(true);
        when(tokenJwtPort.leerClaims("jwt-ok")).thenReturn(new TokenJwtPort.TokenClaims(EMAIL, null, 0));
        when(usuarioRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(usuario));

        UsuarioDTO perfil = consultarPerfilService.getPerfilFromToken("Bearer jwt-ok");

        assertEquals(userId, perfil.getId());
        assertEquals("ODONTOLOGO", perfil.getRol());
        assertEquals("sede-chilca-1", perfil.getSedeId());
    }

    @Test
    @DisplayName("Perfil: token inválido lanza BadCredentials")
    void testPerfilTokenInvalido() {
        when(tokenJwtPort.validateToken("jwt-malo")).thenReturn(false);

        assertThrows(BadCredentialsException.class, () -> consultarPerfilService.getPerfilFromToken("Bearer jwt-malo"));
        verify(usuarioRepository, never()).findByEmailIgnoreCase(any());
    }

    @Test
    @DisplayName("Perfil: usuario desactivado tras emitir el token ya no obtiene su perfil")
    void testPerfilUsuarioInactivo() {
        usuario.setActivo(false);
        when(tokenJwtPort.validateToken("jwt-ok")).thenReturn(true);
        when(tokenJwtPort.leerClaims("jwt-ok")).thenReturn(new TokenJwtPort.TokenClaims(EMAIL, null, 0));
        when(usuarioRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(usuario));

        assertThrows(BadCredentialsException.class, () -> consultarPerfilService.getPerfilFromToken("jwt-ok"));
    }

    @Test
    @DisplayName("Perfil: un token temporal 2FA_PENDING no sirve para consultar el perfil sin completar el segundo factor")
    void testPerfilRechazaTokenTemporal2FA() {
        usuario.setTotpActivado(true);
        when(tokenJwtPort.validateToken("temp-2fa")).thenReturn(true);
        when(tokenJwtPort.leerClaims("temp-2fa")).thenReturn(new TokenJwtPort.TokenClaims(EMAIL, "2FA_PENDING", 0));
        lenient().when(usuarioRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(usuario));

        assertThrows(BadCredentialsException.class, () -> consultarPerfilService.getPerfilFromToken("Bearer temp-2fa"));
        verify(usuarioRepository, never()).findByEmailIgnoreCase(any());
    }
}
