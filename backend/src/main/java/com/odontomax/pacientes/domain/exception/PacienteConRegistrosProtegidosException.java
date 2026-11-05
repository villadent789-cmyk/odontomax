package com.odontomax.pacientes.domain.exception;

public class PacienteConRegistrosProtegidosException extends RuntimeException {
    public PacienteConRegistrosProtegidosException(String message) {
        super(message);
    }
}
