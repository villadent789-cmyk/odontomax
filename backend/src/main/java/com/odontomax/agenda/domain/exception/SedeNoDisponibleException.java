package com.odontomax.agenda.domain.exception;

/** La sede existe pero está inactiva (B-023). */
public class SedeNoDisponibleException extends RuntimeException {
    public SedeNoDisponibleException(String message) {
        super(message);
    }
}
