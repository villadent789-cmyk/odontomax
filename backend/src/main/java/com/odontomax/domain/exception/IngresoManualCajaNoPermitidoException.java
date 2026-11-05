package com.odontomax.domain.exception;

public class IngresoManualCajaNoPermitidoException extends RuntimeException {

    public IngresoManualCajaNoPermitidoException() {
        super("No se permiten ingresos manuales; los ingresos se registran mediante cobros a pacientes.");
    }
}

