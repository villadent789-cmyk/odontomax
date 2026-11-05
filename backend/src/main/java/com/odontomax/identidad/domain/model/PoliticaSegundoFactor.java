package com.odontomax.identidad.domain.model;

import java.util.UUID;

/** Reglas del segundo factor (2FA) en el login. */
public final class PoliticaSegundoFactor {

    /** Intentos de código (TOTP o de respaldo, aciertos incluidos) por token temporal; después queda invalidado. */
    public static final int MAX_INTENTOS_POR_TOKEN = 5;

    /**
     * Intentos de código 2FA por cuenta (todas las IP) en la ventana de fuerza bruta (15 min desde el primero): por
     * encima, el 2FA de esa cuenta queda bloqueado. Se suma al límite de 5 por correo + IP.
     */
    public static final int MAX_INTENTOS_POR_CUENTA = 10;

    private PoliticaSegundoFactor() {
    }

    /**
     * Clave del límite de fallos de las operaciones autenticadas que verifican el 2FA activo (reconfigurar y
     * desactivar): por usuario y compartida entre ambas. 5 fallos bloquean esas operaciones 15 min.
     */
    public static String claveGestion2fa(UUID usuarioId) {
        return "gestion-2fa|" + usuarioId;
    }

    /** Clave del límite por cuenta de /auth/2fa/login (correo normalizado; único por cuenta). */
    public static String claveLogin2faCuenta(String emailNormalizado) {
        return "login-2fa|" + emailNormalizado;
    }
}
