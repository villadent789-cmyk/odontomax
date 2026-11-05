package com.odontomax.reportes.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReporteInventarioResponseDTO {

    private String sedeId;
    private String sedeNombre;
    private Long totalProductos;
    private Long productosStockBajo;
    private Long productosPorVencer;
    private Long productosVencidos;
    private Long transferenciasPendientes;
    private BigDecimal valorizacionTotal;

    @Builder.Default
    private List<ProductoAlertaDTO> alertasStock = new ArrayList<>();

    @Builder.Default
    private List<LoteAlertaDTO> lotesVencimiento = new ArrayList<>();

    @Builder.Default
    private List<TransferenciaResumenDTO> transferencias = new ArrayList<>();

    @Builder.Default
    private List<MovimientoResumenDTO> movimientosRecientes = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ProductoAlertaDTO {
        private UUID productoId;
        private String codigo;
        private String nombre;
        private String categoria;
        private String sedeId;
        private Integer stockActual;
        private Integer stockMinimo;
        private String estado;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LoteAlertaDTO {
        private Long movimientoId;
        private String productoNombre;
        private String lote;
        private LocalDate fechaVencimiento;
        private Integer diasParaVencer;
        private String sedeId;
        private String estado;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TransferenciaResumenDTO {
        private UUID id;
        private String productoNombre;
        private Integer cantidad;
        private String sedeOrigenId;
        private String sedeOrigenNombre;
        private String sedeDestinoId;
        private String sedeDestinoNombre;
        private String estado;
        private LocalDate fecha;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MovimientoResumenDTO {
        private Long id;
        private String productoNombre;
        private String tipo;
        private Integer cantidad;
        private String motivo;
        private String sedeId;
        private LocalDate fecha;
    }
}
