package com.odontomax.application.service;

import com.odontomax.application.dto.response.CajaResumenDiarioDTO;
import com.odontomax.application.dto.response.ReporteDiarioCajaDTO;
import com.odontomax.application.port.out.CajaPeriodoPort;
import com.odontomax.application.port.out.ReporteCajaPort;
import com.odontomax.identidad.api.SecurityUtils;
import com.odontomax.sedes.api.ConsultaSedesModuloApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Reporte diario de caja: lo que entró por método, lo que salió por categoría y cómo cuadró cada cierre. */
@Service
@RequiredArgsConstructor
public class ReporteDiarioCajaService {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    /** Lo que originó cada egreso, con el nombre que el dueño reconoce. */
    private static final Map<String, String> CATEGORIAS = Map.of(
            "GASTO", "Gastos",
            "PAGO_LABORATORIO", "Laboratorio",
            "COMISION_RADIOLOGICA", "Comisiones a doctores",
            "AJUSTE_MANUAL", "Egresos manuales");

    private final CajaPeriodoPort cajaPeriodoPort;
    private final ReporteCajaPort reporteCajaPort;
    private final ConsultaSedesModuloApi sedesApi;

    @Transactional(readOnly = true)
    public ReporteDiarioCajaDTO obtener(String sedeId, LocalDate fecha) {
        SecurityUtils.validarAccesoSede(sedeId);
        LocalDate dia = fecha != null ? fecha : LocalDate.now(LIMA);
        Instant desde = dia.atStartOfDay(LIMA).toInstant();
        Instant hasta = dia.plusDays(1).atStartOfDay(LIMA).toInstant();

        CajaResumenDiarioDTO resumen = cajaPeriodoPort.obtenerResumenDiario(sedeId, desde, hasta);
        ReporteDiarioCajaDTO.PorMetodo ingresos = porMetodo(resumen.getIngresosEfectivo(), resumen.getIngresosYape(), resumen.getIngresosPlin(),
                resumen.getIngresosTarjetaDebito(), resumen.getIngresosTarjetaCredito());
        ReporteDiarioCajaDTO.PorMetodo egresos = porMetodo(resumen.getEgresosEfectivo(), resumen.getEgresosYape(), resumen.getEgresosPlin(),
                resumen.getEgresosTarjeta(), null);

        return ReporteDiarioCajaDTO.builder()
                .fecha(dia)
                .sedeId(sedeId)
                .sedeNombre(sedesApi.obtenerNombreSede(sedeId).orElse(sedeId))
                .ingresos(ingresos)
                .egresos(egresos)
                .balance(ingresos.getTotal().subtract(egresos.getTotal()))
                .egresosPorCategoria(egresosPorCategoria(sedeId, desde, hasta))
                .cierres(reporteCajaPort.cierresDelPeriodo(sedeId, desde, hasta)
                        .stream().map(ReporteDiarioCajaService::cierre).toList())
                .build();
    }

    private List<ReporteDiarioCajaDTO.EgresoCategoria> egresosPorCategoria(String sedeId, Instant desde, Instant hasta) {
        return reporteCajaPort.egresosPorOrigen(sedeId, desde, hasta).stream()
                .map(fila -> ReporteDiarioCajaDTO.EgresoCategoria.builder()
                        .categoria(CATEGORIAS.getOrDefault(fila.origen(), "Otros egresos"))
                        .monto(fila.monto())
                        .build())
                .collect(java.util.stream.Collectors.toMap(ReporteDiarioCajaDTO.EgresoCategoria::getCategoria,
                        e -> e, (a, b) -> ReporteDiarioCajaDTO.EgresoCategoria.builder().categoria(a.getCategoria()).monto(a.getMonto().add(b.getMonto())).build()))
                .values().stream()
                .sorted(Comparator.comparing(ReporteDiarioCajaDTO.EgresoCategoria::getMonto).reversed())
                .toList();
    }

    private static ReporteDiarioCajaDTO.Cierre cierre(ReporteCajaPort.CierreDePeriodo s) {
        return ReporteDiarioCajaDTO.Cierre.builder()
                .cerradaEn(s.cerradaEn())
                .efectivo(arqueo(s.esperadoEfectivo(), s.contadoEfectivo()))
                .yape(arqueo(s.esperadoYape(), s.contadoYape()))
                .plin(arqueo(s.esperadoPlin(), s.contadoPlin()))
                .tarjeta(arqueo(s.esperadoTarjeta(), s.contadoTarjeta()))
                .observaciones(s.observaciones())
                .build();
    }

    private static ReporteDiarioCajaDTO.Arqueo arqueo(BigDecimal esperado, BigDecimal contado) {
        BigDecimal e = cero(esperado);
        BigDecimal c = cero(contado);
        return ReporteDiarioCajaDTO.Arqueo.builder().esperado(e).contado(c).diferencia(c.subtract(e)).build();
    }

    private static ReporteDiarioCajaDTO.PorMetodo porMetodo(BigDecimal efectivo, BigDecimal yape, BigDecimal plin,
                                                           BigDecimal tarjetaDebito, BigDecimal tarjetaCredito) {
        BigDecimal e = cero(efectivo);
        BigDecimal y = cero(yape);
        BigDecimal p = cero(plin);
        BigDecimal td = cero(tarjetaDebito);
        BigDecimal tc = cero(tarjetaCredito);
        return ReporteDiarioCajaDTO.PorMetodo.builder().efectivo(e).yape(y).plin(p).tarjetaDebito(td).tarjetaCredito(tc)
                .total(e.add(y).add(p).add(td).add(tc)).build();
    }

    private static BigDecimal cero(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
