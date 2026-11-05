package com.odontomax.domain.exception;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Excepción de dominio lanzada cuando se intenta emitir un comprobante electrónico (Boleta o Factura)
 * para un tratamiento que aún cuenta con saldo pendiente de pago.
 */
public class ComprobanteSaldoPendienteException extends RuntimeException {

    private final BigDecimal saldoPendiente;

    public ComprobanteSaldoPendienteException(BigDecimal saldoPendiente) {
        super(String.format("Falta pagar S/ %s para emitir el comprobante",
                saldoPendiente != null ? saldoPendiente.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP).toPlainString() : "0.00"));
        this.saldoPendiente = saldoPendiente != null ? saldoPendiente : BigDecimal.ZERO;
    }

    public BigDecimal getSaldoPendiente() {
        return saldoPendiente;
    }
}
