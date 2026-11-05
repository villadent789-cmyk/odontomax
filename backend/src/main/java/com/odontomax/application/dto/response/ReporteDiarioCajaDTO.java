package com.odontomax.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Reporte diario de caja de una sede: ingresos por método, egresos por categoría y la diferencia de cada arqueo. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReporteDiarioCajaDTO {

    private LocalDate fecha;
    private String sedeId;
    private String sedeNombre;
    private PorMetodo ingresos;
    private PorMetodo egresos;
    private BigDecimal balance;
    private List<EgresoCategoria> egresosPorCategoria;
    private List<Cierre> cierres;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PorMetodo {
        private BigDecimal efectivo;
        private BigDecimal yape;
        private BigDecimal plin;
        private BigDecimal tarjetaDebito;
        private BigDecimal tarjetaCredito;
        private BigDecimal total;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EgresoCategoria {
        private String categoria;
        private BigDecimal monto;
    }

    /** Un cierre de periodo del día con lo esperado, lo contado y la diferencia de cada método. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Cierre {
        private Instant cerradaEn;
        private Arqueo efectivo;
        private Arqueo yape;
        private Arqueo plin;
        /** Tarjetas de débito y crédito juntas, contra el cierre del POS. */
        private Arqueo tarjeta;
        private String observaciones;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Arqueo {
        private BigDecimal esperado;
        private BigDecimal contado;
        /** Contado menos esperado: positivo = sobrante, negativo = faltante. */
        private BigDecimal diferencia;
    }
}
