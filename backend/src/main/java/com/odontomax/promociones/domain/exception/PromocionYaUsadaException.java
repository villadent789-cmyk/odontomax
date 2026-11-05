package com.odontomax.promociones.domain.exception;

public class PromocionYaUsadaException extends RuntimeException {
    public PromocionYaUsadaException(String mensaje) {
        super(mensaje);
    }
}
