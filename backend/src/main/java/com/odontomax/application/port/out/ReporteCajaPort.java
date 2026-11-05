package com.odontomax.application.port.out;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Consultas de solo lectura que alimentan el reporte diario de caja. */
public interface ReporteCajaPort {

    /** Egresos del periodo sumados por lo que los originó (GASTO, PAGO_LABORATORIO…); la clave puede ser nula. */
    List<EgresoPorOrigen> egresosPorOrigen(String sedeId, Instant desde, Instant hasta);

    /** Cierres de periodo hechos dentro del rango, del más antiguo al más reciente. */
    List<CierreDePeriodo> cierresDelPeriodo(String sedeId, Instant desde, Instant hasta);

    record EgresoPorOrigen(String origen, BigDecimal monto) {
    }

    record CierreDePeriodo(Instant cerradaEn,
                           BigDecimal esperadoEfectivo, BigDecimal contadoEfectivo,
                           BigDecimal esperadoYape, BigDecimal contadoYape,
                           BigDecimal esperadoPlin, BigDecimal contadoPlin,
                           BigDecimal esperadoTarjeta, BigDecimal contadoTarjeta,
                           String observaciones) {
    }
}
