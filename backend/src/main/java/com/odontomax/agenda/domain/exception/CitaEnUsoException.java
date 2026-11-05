package com.odontomax.agenda.domain.exception;

/**
 * La cita está bloqueada por otra operación que no terminó a tiempo (B-039). El manejador global la traduce a 409:
 * el cliente puede reintentar.
 */
public class CitaEnUsoException extends RuntimeException {
    public CitaEnUsoException() {
        super("La cita está siendo modificada por otra operación, inténtelo de nuevo.");
    }
}
