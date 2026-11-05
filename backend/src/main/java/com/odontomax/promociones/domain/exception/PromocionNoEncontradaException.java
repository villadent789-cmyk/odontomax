package com.odontomax.promociones.domain.exception;

public class PromocionNoEncontradaException extends RuntimeException {
    public PromocionNoEncontradaException(String mensaje) {
        super(mensaje);
    }
}
