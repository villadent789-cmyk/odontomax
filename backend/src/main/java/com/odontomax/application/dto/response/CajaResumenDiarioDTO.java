package com.odontomax.application.dto.response;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;

@Value
@Builder
public class CajaResumenDiarioDTO {
    LocalDate fecha;
    String sedeId;
    BigDecimal ingresosEfectivo;
    BigDecimal ingresosYape;
    BigDecimal ingresosPlin;
    BigDecimal egresosEfectivo;
    BigDecimal egresosYape;
    BigDecimal egresosPlin;
    BigDecimal ingresosTarjetaDebito;
    BigDecimal ingresosTarjetaCredito;
    BigDecimal egresosTarjeta;
}
