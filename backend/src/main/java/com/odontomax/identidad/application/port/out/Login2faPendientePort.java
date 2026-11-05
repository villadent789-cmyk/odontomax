package com.odontomax.identidad.application.port.out;

/**
 * Estado de los logins pendientes de segundo factor (token temporal {@code 2FA_PENDING}).
 * La clave es el hash del token temporal: nunca se guarda el token en claro.
 */
public interface Login2faPendientePort {

    /**
     * Reserva de forma atómica un intento de código para el login pendiente, ANTES de verificar el código (así N
     * peticiones concurrentes con el mismo token no pueden probar más de {@code maximo} códigos).
     *
     * @return {@code true} si el intento se rechaza: el login pendiente está invalidado (completado) o ya se
     * reservaron {@code maximo} intentos; {@code false} si queda reservado y el código se puede verificar
     */
    boolean intentoRechazado(String clave, int maximo);

    /** Intentos reservados hasta ahora para el login pendiente (0 si no hay ninguno). */
    int intentosReservados(String clave);

    /**
     * Invalida el login pendiente: el token temporal deja de servir.
     *
     * @return {@code true} si ya estaba invalidado antes de esta llamada
     */
    boolean invalidar(String clave);
}
