package com.odontomax.application.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventarioMovimientoResponseDTO {

    private Long id;

    @JsonProperty("producto_id")
    private UUID productoId;

    @JsonProperty("producto_nombre")
    private String productoNombre;

    @JsonProperty("sede_id")
    private String sedeId;

    private String tipo;

    private Integer cantidad;

    private String lote;

    @JsonProperty("fecha_vencimiento")
    private LocalDate fechaVencimiento;

    @JsonProperty("fecha_entrega")
    private LocalDate fechaEntrega;

    private String proveedor;

    private String motivo;

    @JsonProperty("registrado_por")
    private UUID registradoPor;

    @JsonProperty("creado_en")
    private OffsetDateTime creadoEn;

    @JsonProperty("productoId")
    public UUID getProductoIdCamel() { return productoId; }

    @JsonProperty("productoNombre")
    public String getProductoNombreCamel() { return productoNombre; }

    @JsonProperty("sedeId")
    public String getSedeIdCamel() { return sedeId; }

    @JsonProperty("fechaVencimiento")
    public LocalDate getFechaVencimientoCamel() { return fechaVencimiento; }

    @JsonProperty("fechaEntrega")
    public LocalDate getFechaEntregaCamel() { return fechaEntrega; }

    @JsonProperty("registradoPor")
    public UUID getRegistradoPorCamel() { return registradoPor; }

    @JsonProperty("creadoEn")
    public OffsetDateTime getCreadoEnCamel() { return creadoEn; }
}
