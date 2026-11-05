package com.odontomax.tratamientos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PresupuestoResponseDTO {
    private UUID id;
    private UUID pacienteId;
    private String sedeId;
    private UUID especialistaId;
    private String especialistaNombre;
    private String motivo;
    private String notas;
    /** VIGENTE, ACEPTADO o RECHAZADO. */
    private String estado;
    private BigDecimal total;
    /** Total de las líneas aceptadas; en un presupuesto vigente, cero. */
    private BigDecimal totalAceptado;
    /** Plan creado con lo aceptado. */
    private UUID tratamientoId;
    private String creadoPor;
    private Instant creadoEn;
    private Instant resueltoEn;
    private List<Item> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private UUID id;
        private String procedimientoId;
        private String nombre;
        private Integer piezaFdi;
        private String gravedad;
        private int cantidad;
        private BigDecimal precioUnitario;
        /** Cantidad × costo por pieza. */
        private BigDecimal subtotal;
        private boolean aceptado;
    }
}
