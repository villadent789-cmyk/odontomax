package com.odontomax.pacientes.domain.exception;

public class PacienteNoEncontradoException extends RuntimeException {
    public PacienteNoEncontradoException(String message) {
        super(message);
    }
}