package com.odontomax.identidad.application.port.out;

import java.util.UUID;

/** Emisión y lectura de JWT de sesión y del token temporal de 2FA. */
public interface TokenJwtPort {

    /** @param tokenVersion versión de credenciales vigente del usuario (claim {@code tokenVersion}) */
    String generateToken(UUID userId, String email, String nombre, String rol, String sedeId, int tokenVersion);

    String generateTemp2FaToken(UUID userId, String email, String nombre, String rol, int tokenVersion);

    boolean validateToken(String token);

    /** Claims que usa identidad. Solo debe llamarse con un token ya validado. */
    TokenClaims leerClaims(String token);

    long getExpirationMs();

    /**
     * @param tipo         claim {@code type} ({@code 2FA_PENDING} en el token temporal; nulo en el de sesión)
     * @param tokenVersion claim {@code tokenVersion}; 0 si el token no lo lleva (emitido antes de B-008)
     */
    record TokenClaims(String subject, String tipo, int tokenVersion) {
    }
}
