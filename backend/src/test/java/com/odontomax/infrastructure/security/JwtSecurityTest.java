package com.odontomax.infrastructure.security;

import com.odontomax.identidad.infrastructure.security.JwtTokenProvider;
import com.odontomax.identidad.infrastructure.security.TotpService;
import com.odontomax.application.dto.request.LoginRequestDTO;
import com.odontomax.application.dto.response.LoginResponseDTO;
import com.odontomax.identidad.application.service.LoginService;
import com.odontomax.identidad.infrastructure.adapter.InMemoryBruteForceAdapter;
import com.odontomax.identidad.infrastructure.adapter.InMemoryLogin2faPendienteAdapter;
import com.odontomax.identidad.infrastructure.adapter.InMemoryTotpPasoUsadoAdapter;
import com.odontomax.identidad.infrastructure.persistence.adapter.UsuarioJpaAdapter;
import com.odontomax.identidad.infrastructure.persistence.mapper.UsuarioMapperImpl;
import com.odontomax.identidad.infrastructure.persistence.entity.UsuarioEntity;
import com.odontomax.identidad.infrastructure.persistence.repository.SpringDataUsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class JwtSecurityTest {

    private JwtTokenProvider jwtTokenProvider;
    private SpringDataUsuarioRepository usuarioRepository;
    private LoginService authService;
    private BCryptPasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider();
        byte[] randomSecretBytes = new byte[64];
        new java.security.SecureRandom().nextBytes(randomSecretBytes);
        String randomJwtSecret = java.util.Base64.getEncoder().encodeToString(randomSecretBytes);
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtSecret", randomJwtSecret);
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtExpirationMs", 3600000L); // 1 hora
        jwtTokenProvider.init();

        usuarioRepository = mock(SpringDataUsuarioRepository.class);
        passwordEncoder = new BCryptPasswordEncoder();
        authService = new LoginService(new UsuarioJpaAdapter(usuarioRepository, new UsuarioMapperImpl()),
                jwtTokenProvider, passwordEncoder, new InMemoryBruteForceAdapter(), new TotpService(),
                new InMemoryLogin2faPendienteAdapter(), new InMemoryTotpPasoUsadoAdapter());
    }

    @Test
    @DisplayName("Debe generar un token JWT válido y extraer los claims del personal clínico")
    void testGenerarYValidarToken() {
        UUID doctorId = UUID.randomUUID();
        String token = jwtTokenProvider.generateToken(
                doctorId,
                "doctor.rios@odontomax.pe",
                "Dr. Alex Ríos",
                "ODONTOLOGO",
                "sede-chilca-1"
        , 0);

        assertNotNull(token);
        assertTrue(jwtTokenProvider.validateToken(token));
        assertEquals("doctor.rios@odontomax.pe", jwtTokenProvider.getEmailFromToken(token));
        assertEquals("ODONTOLOGO", jwtTokenProvider.getRolFromToken(token));
        assertEquals("sede-chilca-1", jwtTokenProvider.getSedeIdFromToken(token));
    }

    @Test
    @DisplayName("Debe rechazar tokens manipulados o alterados criptográficamente")
    void testRechazarTokenAlterado() {
        UUID userId = UUID.randomUUID();
        String token = jwtTokenProvider.generateToken(
                userId,
                "test@odontomax.pe",
                "Usuario Test",
                "RECEPCION",
                "sede-chilca-1"
        , 0);

        // Alterar el payload del token (manipulación en cliente)
        String tokenAdulterado = token + "xyz";
        assertFalse(jwtTokenProvider.validateToken(tokenAdulterado));
    }

    @Test
    @DisplayName("AuthService debe autenticar correctamente contra BD con BCrypt y devolver JWT firmado")
    void testLoginExitoso() {
        String rawPassword = "TestSecurePassword_" + UUID.randomUUID();
        String encodedHash = passwordEncoder.encode(rawPassword);

        UsuarioEntity mockUser = UsuarioEntity.builder()
                .id(UUID.randomUUID())
                .email("admin@odontomax.pe")
                .nombre("Dr. Alex Ríos")
                .passwordHash(encodedHash)
                .rol("ADMIN_GENERAL")
                .sedeId(null)
                .activo(true)
                .build();

        when(usuarioRepository.findByEmailIgnoreCase("admin@odontomax.pe")).thenReturn(Optional.of(mockUser));
        when(usuarioRepository.save(any(UsuarioEntity.class))).thenReturn(mockUser);

        LoginRequestDTO request = LoginRequestDTO.builder()
                .email("admin@odontomax.pe")
                .password(rawPassword)
                .build();

        LoginResponseDTO response = authService.login(request);

        assertNotNull(response);
        assertNotNull(response.getToken());
        assertEquals("Bearer", response.getTokenTipo());
        assertEquals("admin@odontomax.pe", response.getUsuario().getEmail());
        assertEquals("ADMIN_GENERAL", response.getUsuario().getRol());
        assertTrue(jwtTokenProvider.validateToken(response.getToken()));
    }

    @Test
    @DisplayName("AuthService debe rechazar credenciales incorrectas con BadCredentialsException")
    void testLoginFallido() {
        String testPassword = "InitialTestPass_" + UUID.randomUUID();
        UsuarioEntity mockUser = UsuarioEntity.builder()
                .id(UUID.randomUUID())
                .email("admin@odontomax.pe")
                .passwordHash(passwordEncoder.encode(testPassword))
                .rol("ADMIN_GENERAL")
                .activo(true)
                .build();

        when(usuarioRepository.findByEmailIgnoreCase("admin@odontomax.pe")).thenReturn(Optional.of(mockUser));

        LoginRequestDTO request = LoginRequestDTO.builder()
                .email("admin@odontomax.pe")
                .password("ClaveErronea123")
                .build();

        assertThrows(BadCredentialsException.class, () -> authService.login(request));
    }
}
