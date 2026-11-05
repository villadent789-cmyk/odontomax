package com.odontomax.infrastructure.security;

import com.odontomax.identidad.infrastructure.security.JwtTokenProvider;
import com.odontomax.identidad.application.port.out.TokenJwtPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** JwtTokenProvider como implementación de TokenJwtPort: leerClaims devuelve subject y claim "type". */
class JwtTokenProviderPortTest {

    private TokenJwtPort port;

    @BeforeEach
    void setUp() {
        JwtTokenProvider provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "jwtSecret", "clave-de-prueba-de-al-menos-32-caracteres!!");
        ReflectionTestUtils.setField(provider, "jwtExpirationMs", 60_000L);
        provider.init();
        port = provider;
    }

    @Test
    @DisplayName("Token de sesión: subject = email y tipo nulo")
    void tokenDeSesion() {
        String token = port.generateToken(UUID.randomUUID(), "ana@odontomax.pe", "Ana", "ODONTOLOGO", "sede-1", 0);

        assertThat(port.validateToken(token)).isTrue();
        assertThat(port.leerClaims(token)).isEqualTo(new TokenJwtPort.TokenClaims("ana@odontomax.pe", null, 0));
        assertThat(port.getExpirationMs()).isEqualTo(60_000L);
    }

    @Test
    @DisplayName("Token temporal de 2FA: tipo 2FA_PENDING")
    void tokenTemporal2fa() {
        String token = port.generateTemp2FaToken(UUID.randomUUID(), "ana@odontomax.pe", "Ana", "ADMIN_GENERAL", 0);

        assertThat(port.leerClaims(token)).isEqualTo(new TokenJwtPort.TokenClaims("ana@odontomax.pe", "2FA_PENDING", 0));
    }

    @Test
    @DisplayName("Token manipulado no es válido")
    void tokenManipulado() {
        String token = port.generateToken(UUID.randomUUID(), "ana@odontomax.pe", "Ana", "ODONTOLOGO", "sede-1", 0);

        assertThat(port.validateToken(token + "x")).isFalse();
    }
}
