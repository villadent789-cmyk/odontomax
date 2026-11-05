package com.odontomax.infrastructure.security;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Inicializador de contexto de pruebas que genera de manera segura y aleatoria
 * en tiempo de ejecución las claves criptográficas para JWT y TOTP (AES-256-GCM),
 * impidiendo la presencia de cualquier literal o clave fija en el código fuente.
 */
public class TestSecurityEnvironmentInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        byte[] jwtSecretBytes = new byte[64];
        byte[] totpKeyBytes = new byte[32];
        SecureRandom random = new SecureRandom();
        random.nextBytes(jwtSecretBytes);
        random.nextBytes(totpKeyBytes);

        String randomJwtSecret = Base64.getEncoder().encodeToString(jwtSecretBytes);
        String randomTotpKey = Base64.getEncoder().encodeToString(totpKeyBytes);

        TestPropertyValues.of(
                "security.jwt.secret=" + randomJwtSecret,
                "JWT_SECRET=" + randomJwtSecret,
                "security.totp.encryption-key=" + randomTotpKey,
                "TOTP_ENCRYPTION_KEY=" + randomTotpKey
        ).applyTo(applicationContext.getEnvironment());
    }
}
