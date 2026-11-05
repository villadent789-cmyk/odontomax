package com.odontomax.application.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductoInventarioResponseDTO {

    private UUID id;
    private String codigo;
    private String nombre;
    private String categoria;
    private String unidad;

    @JsonProperty("precio_unitario")
    private BigDecimal precioUnitario;

    @JsonProperty("stock_minimo")
    private Integer stockMinimo;

    private boolean activo;

    @JsonProperty("stockPorSede")
    private Map<String, Integer> stockPorSede;

    @JsonProperty("stockTotal")
    private Integer stockTotal;

    @JsonProperty("alertaStockBajo")
    private boolean alertaStockBajo;

    @JsonProperty("actualizado_en")
    private OffsetDateTime actualizadoEn;

    /** Vencimiento futuro más cercano entre las entradas del producto; null si ninguna lo informa. */
    private LocalDate proximoVencimiento;

    /** Fecha de entrega más reciente entre las entradas del producto; null si ninguna la informa. */
    private LocalDate ultimaEntrega;

    // Helper getters for dual camelCase / snake_case serialization
    @JsonProperty("precioUnitario")
    public BigDecimal getPrecioUnitarioCamel() {
        return precioUnitario;
    }

    @JsonProperty("stockMinimo")
    public Integer getStockMinimoCamel() {
        return stockMinimo;
    }

    @JsonProperty("actualizadoEn")
    public OffsetDateTime getActualizadoEnCamel() {
        return actualizadoEn;
    }
}
