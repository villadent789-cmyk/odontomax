package com.odontomax.domain.exception;

public class PagoSinTratamientoException extends RuntimeException {

    public PagoSinTratamientoException() {
        super("El cobro debe pertenecer a un plan de tratamiento");
    }
}
