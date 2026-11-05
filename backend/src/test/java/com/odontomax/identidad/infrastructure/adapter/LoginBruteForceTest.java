package com.odontomax.identidad.infrastructure.adapter;

import com.odontomax.application.dto.request.LoginRequestDTO;
import com.odontomax.identidad.application.port.out.TokenJwtPort;
import com.odontomax.identidad.application.port.out.TotpPort;
import com.odontomax.identidad.application.port.out.UsuarioRepository;
import com.odontomax.identidad.application.service.LoginService;
import com.odontomax.identidad.domain.model.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Bloqueo por fuerza bruta del login (reescritura 1:1 de los casos de AuthServiceTest): LoginService con el
 * contador real {@link InMemoryBruteForceAdapter} y el resto de puertos simulados.
 */
@ExtendWith(MockitoExtension.class)
class LoginBruteForceTest {

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
    private TotpPort totpPort;

    private InMemoryBruteForceAdapter bruteForce;
    private LoginService authService;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        bruteForce = new InMemoryBruteForceAdapter();
        authService = new LoginService(usuarioRepository, tokenJwtPort, passwordEncoder, bruteForce, totpPort,
                new InMemoryLogin2faPendienteAdapter(), new InMemoryTotpPasoUsadoAdapter());
        usuario = Usuario.builder()
                .id(UUID.randomUUID())
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

    private void fallarLogin(String password, String ip) {
        assertThrows(BadCredentialsException.class,
                () -> authService.login(credenciales(EMAIL, password), ip));
    }

    @Test
    @DisplayName("Bloqueo: tras 5 intentos fallidos el 6º se bloquea aunque la contraseña sea correcta, sin consultar la BD")
    void testBloqueoTrasCincoIntentosFallidos() {
        when(usuarioRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("Erronea123!", HASH)).thenReturn(false);

        for (int i = 0; i < 5; i++) {
            fallarLogin("Erronea123!", IP);
        }

        BadCredentialsException ex = assertThrows(BadCredentialsException.class,
                () -> authService.login(credenciales(EMAIL, "Correcta123!"), IP));

        assertTrue(ex.getMessage().contains("Demasiados intentos fallidos"));
        assertTrue(ex.getMessage().contains("15 minutos") || ex.getMessage().contains("14 minutos"));
        verify(usuarioRepository, times(5)).findByEmailIgnoreCase(EMAIL);
        verify(passwordEncoder, never()).matches("Correcta123!", HASH);
    }

    @Test
    @DisplayName("Bloqueo: 4 intentos fallidos aún permiten iniciar sesión con la contraseña correcta")
    void testCuatroIntentosNoBloquean() {
        when(usuarioRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("Erronea123!", HASH)).thenReturn(false);
        when(passwordEncoder.matches("Correcta123!", HASH)).thenReturn(true);
        when(tokenJwtPort.generateToken(any(), any(), any(), any(), any(), anyInt())).thenReturn("jwt-final");

        for (int i = 0; i < 4; i++) {
            fallarLogin("Erronea123!", IP);
        }

        assertEquals("jwt-final", authService.login(credenciales(EMAIL, "Correcta123!"), IP).getToken());
    }

    @Test
    @DisplayName("Bloqueo: un login exitoso reinicia el contador de intentos fallidos")
    void testLoginExitosoReiniciaContador() {
        when(usuarioRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("Erronea123!", HASH)).thenReturn(false);
        when(passwordEncoder.matches("Correcta123!", HASH)).thenReturn(true);
        when(tokenJwtPort.generateToken(any(), any(), any(), any(), any(), anyInt())).thenReturn("jwt-final");

        for (int i = 0; i < 4; i++) {
            fallarLogin("Erronea123!", IP);
        }
        authService.login(credenciales(EMAIL, "Correcta123!"), IP);
        for (int i = 0; i < 4; i++) {
            fallarLogin("Erronea123!", IP);
        }

        // Sin reinicio serían 8 fallos acumulados y este intento quedaría bloqueado
        assertEquals("jwt-final", authService.login(credenciales(EMAIL, "Correcta123!"), IP).getToken());
    }

    @Test
    @DisplayName("Bloqueo: la clave es correo + IP; otra IP con el mismo correo no queda bloqueada")
    void testBloqueoAisladoPorIp() {
        when(usuarioRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("Erronea123!", HASH)).thenReturn(false);
        when(passwordEncoder.matches("Correcta123!", HASH)).thenReturn(true);
        when(tokenJwtPort.generateToken(any(), any(), any(), any(), any(), anyInt())).thenReturn("jwt-final");

        for (int i = 0; i < 5; i++) {
            fallarLogin("Erronea123!", IP);
        }

        assertEquals("jwt-final", authService.login(credenciales(EMAIL, "Correcta123!"), "10.0.0.2").getToken());
        assertThrows(BadCredentialsException.class,
                () -> authService.login(credenciales(EMAIL, "Correcta123!"), IP));
    }

    @Test
    @DisplayName("Bloqueo: el correo se normaliza, variar mayúsculas/espacios no evade el bloqueo")
    void testBloqueoNoSeEvadeConMayusculas() {
        when(usuarioRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("Erronea123!", HASH)).thenReturn(false);

        String[] variantes = {EMAIL, "DOCTOR@odontomax.pe", " doctor@ODONTOMAX.pe", "Doctor@OdontoMax.PE ", "doctor@odontomax.pe"};
        for (String variante : variantes) {
            assertThrows(BadCredentialsException.class,
                    () -> authService.login(credenciales(variante, "Erronea123!"), IP));
        }

        BadCredentialsException ex = assertThrows(BadCredentialsException.class,
                () -> authService.login(credenciales("DoCtOr@odontomax.pe", "Correcta123!"), IP));
        assertTrue(ex.getMessage().contains("Demasiados intentos fallidos"));
    }

    @Test
    @DisplayName("Bloqueo: los intentos con correos inexistentes también cuentan (evita enumeración por fuerza bruta)")
    void testBloqueoCuentaCorreosInexistentes() {
        when(usuarioRepository.findByEmailIgnoreCase("fantasma@odontomax.pe")).thenReturn(Optional.empty());

        for (int i = 0; i < 5; i++) {
            assertThrows(BadCredentialsException.class,
                    () -> authService.login(credenciales("fantasma@odontomax.pe", "x"), IP));
        }

        BadCredentialsException ex = assertThrows(BadCredentialsException.class,
                () -> authService.login(credenciales("fantasma@odontomax.pe", "x"), IP));
        assertTrue(ex.getMessage().contains("Demasiados intentos fallidos"));
        verify(usuarioRepository, times(5)).findByEmailIgnoreCase("fantasma@odontomax.pe");
    }

    @Test
    @DisplayName("Bloqueo: IP nula o en blanco se agrupan bajo la misma clave 'unknown'")
    void testBloqueoIpDesconocida() {
        when(usuarioRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("Erronea123!", HASH)).thenReturn(false);

        fallarLogin("Erronea123!", null);
        fallarLogin("Erronea123!", "   ");
        fallarLogin("Erronea123!", null);
        fallarLogin("Erronea123!", "");
        fallarLogin("Erronea123!", null);

        BadCredentialsException ex = assertThrows(BadCredentialsException.class,
                () -> authService.login(credenciales(EMAIL, "Correcta123!"), "  "));
        assertTrue(ex.getMessage().contains("Demasiados intentos fallidos"));
    }

    @Test
    @DisplayName("Bloqueo: purgeExpiredAttempts no libera bloqueos todavía vigentes")
    void testPurgaNoLiberaBloqueoVigente() {
        when(usuarioRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("Erronea123!", HASH)).thenReturn(false);
        for (int i = 0; i < 5; i++) {
            fallarLogin("Erronea123!", IP);
        }

        bruteForce.purgeExpiredAttempts();

        BadCredentialsException ex = assertThrows(BadCredentialsException.class,
                () -> authService.login(credenciales(EMAIL, "Correcta123!"), IP));
        assertTrue(ex.getMessage().contains("Demasiados intentos fallidos"));
    }
}
