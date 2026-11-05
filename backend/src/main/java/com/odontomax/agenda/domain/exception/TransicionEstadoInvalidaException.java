package com.odontomax.agenda.domain.exception;

/**
 * Cambio de estado de una cita que el flujo no permite (B-028). Es un error de negocio: hereda de
 * {@link IllegalArgumentException} y el manejador global lo traduce a 400 igual que las demás reglas de la cita.
 */
public class TransicionEstadoInvalidaException extends IllegalArgumentException {
    public TransicionEstadoInvalidaException(String message) {
        super(message);
    }
}
