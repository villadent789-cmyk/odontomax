package com.odontomax.application.port.out;

import java.math.BigDecimal;

public record CajaTotalesSesion(
        BigDecimal ingresosEfectivo,
        BigDecimal egresosEfectivo,
        BigDecimal ingresosYape,
        BigDecimal egresosYape,
        BigDecimal ingresosPlin,
        BigDecimal egresosPlin,
        BigDecimal montoEsperadoYape,
        BigDecimal montoContadoYape,
        BigDecimal diferenciaYape,
        BigDecimal montoEsperadoPlin,
        BigDecimal montoContadoPlin,
        BigDecimal diferenciaPlin,
        BigDecimal ingresosTarjetaDebito,
        BigDecimal ingresosTarjetaCredito,
        BigDecimal egresosTarjeta,
        BigDecimal montoEsperadoTarjeta,
        BigDecimal montoContadoTarjeta,
        BigDecimal diferenciaTarjeta) {

    public BigDecimal montoEsperadoEfectivo() {
        return ingresosEfectivo.subtract(egresosEfectivo);
    }
}
