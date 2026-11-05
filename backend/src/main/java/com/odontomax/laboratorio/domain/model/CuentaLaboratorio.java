package com.odontomax.laboratorio.domain.model;

import com.odontomax.laboratorio.domain.exception.PagoLaboratorioNoPermitidoException;

import java.math.BigDecimal;

public final class CuentaLaboratorio {
    private CuentaLaboratorio() {
    }

    public static BigDecimal validarPago(String estadoOrden, BigDecimal costo, BigDecimal pagado, BigDecimal monto) {
        if (costo == null) {
            throw new PagoLaboratorioNoPermitidoException("No se puede registrar un pago: la orden no tiene costo del laboratorio.");
        }
        if ("CANCELADO".equalsIgnoreCase(estadoOrden)) {
            throw new PagoLaboratorioNoPermitidoException("No se puede registrar un pago a una orden cancelada.");
        }
        if (monto == null || monto.signum() <= 0) {
            throw new PagoLaboratorioNoPermitidoException("El monto del pago debe ser mayor a cero.");
        }

        BigDecimal saldo = costo.subtract(pagado != null ? pagado : BigDecimal.ZERO).max(BigDecimal.ZERO);
        if (monto.compareTo(saldo) > 0) {
            throw new PagoLaboratorioNoPermitidoException(
                    "El pago excede el saldo pendiente del laboratorio: S/ " + saldo.toPlainString() + ".");
        }
        return saldo;
    }

    public static BigDecimal saldo(BigDecimal costo, BigDecimal pagado) {
        if (costo == null) {
            return null;
        }
        return costo.subtract(pagado != null ? pagado : BigDecimal.ZERO).max(BigDecimal.ZERO);
    }

    public static void validarAnulacion(Boolean yaAnulado, String motivoAnulacion) {
        if (Boolean.TRUE.equals(yaAnulado)) {
            throw new PagoLaboratorioNoPermitidoException("El pago de laboratorio ya ha sido anulado previamente.");
        }
        if (motivoAnulacion == null || motivoAnulacion.trim().isEmpty()) {
            throw new PagoLaboratorioNoPermitidoException("El motivo de anulación es obligatorio.");
        }
    }
}
