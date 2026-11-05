package com.odontomax.agenda.domain.exception;

/** El número de sillón no está entre 1 y los sillones de la sede (B-023). */
public class SillonInexistenteException extends RuntimeException {
    public SillonInexistenteException(String message) {
        super(message);
    }
}
