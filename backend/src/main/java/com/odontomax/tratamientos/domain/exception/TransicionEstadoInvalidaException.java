package com.odontomax.tratamientos.domain.exception;

/**
 * Excepción lanzada cuando una transición de estado de un tratamiento no es permitida por la máquina de estados.
 */
public class TransicionEstadoInvalidaException extends IllegalArgumentException {
    public TransicionEstadoInvalidaException(String message) {
        super(message);
    }
}
