package com.odontomax.domain.exception;

public class DiferenciaCajaSinObservacionesException extends RuntimeException {

    public DiferenciaCajaSinObservacionesException() {
        super("Las observaciones son obligatorias cuando existe una diferencia en el cierre de caja.");
    }
}
