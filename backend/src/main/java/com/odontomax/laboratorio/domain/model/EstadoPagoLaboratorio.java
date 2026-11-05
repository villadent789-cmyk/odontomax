package com.odontomax.laboratorio.domain.model;

import java.math.BigDecimal;

public enum EstadoPagoLaboratorio {
    PENDIENTE,
    PARCIAL,
    PAGADO;

    public static EstadoPagoLaboratorio derivar(BigDecimal costo, BigDecimal pagado) {
        if (costo == null) {
            return null;
        }
        BigDecimal montoPagado = pagado != null ? pagado : BigDecimal.ZERO;
        if (montoPagado.signum() <= 0) {
            return PENDIENTE;
        }
        return montoPagado.compareTo(costo) < 0 ? PARCIAL : PAGADO;
    }
}
