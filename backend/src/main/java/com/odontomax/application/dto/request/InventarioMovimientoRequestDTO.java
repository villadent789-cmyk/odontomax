package com.odontomax.application.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventarioMovimientoRequestDTO {

    @JsonProperty("producto_id")
    @NotNull(message = "El ID del producto es obligatorio")
    private UUID productoId;

    @JsonProperty("sede_id")
    @NotBlank(message = "La sede es obligatoria")
    private String sedeId;

    @NotBlank(message = "El tipo de movimiento es obligatorio")
    @Pattern(regexp = "^(ENTRADA|SALIDA|AJUSTE)$", message = "El tipo debe ser ENTRADA, SALIDA o AJUSTE")
    private String tipo;

    @NotNull(message = "La cantidad es obligatoria")
    private Integer cantidad;

    private String lote;

    @JsonProperty("fecha_vencimiento")
    private LocalDate fechaVencimiento;

    @JsonProperty("fecha_entrega")
    private LocalDate fechaEntrega;

    private String proveedor;

    private String motivo;

    @JsonProperty("cita_id")
    private UUID citaId;

    @JsonProperty("tratamiento_id")
    private UUID tratamientoId;
}
