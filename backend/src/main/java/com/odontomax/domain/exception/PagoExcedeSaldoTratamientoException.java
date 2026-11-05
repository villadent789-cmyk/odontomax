package com.odontomax.domain.exception;

import java.math.BigDecimal;

public class PagoExcedeSaldoTratamientoException extends RuntimeException {

    public PagoExcedeSaldoTratamientoException(BigDecimal saldoDisponible) {
        super("El monto supera el saldo del plan. Saldo disponible: S/ " + saldoDisponible.max(BigDecimal.ZERO).toPlainString());
    }
}
