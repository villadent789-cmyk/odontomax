package com.odontomax.application.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TratamientoItemRequestDTO {

    private String procedimientoId;

    @NotBlank(message = "El nombre del procedimiento es obligatorio")
    private String nombreProcedimiento;

    private Integer piezaFdi;

    @NotNull(message = "El precio base es obligatorio")
    @DecimalMin(value = "0.00", message = "El precio base no puede ser negativo")
    private BigDecimal precioBase;

    @Builder.Default
    private Integer cantidad = 1;

    @Builder.Default
    private BigDecimal descuento = BigDecimal.ZERO;

    private String observaciones;

    /** Gravedad del caso (LEVE, MODERADO o SEVERO), si el costo de la pieza depende de ella. */
    @jakarta.validation.constraints.Pattern(regexp = "LEVE|MODERADO|SEVERO", message = "La gravedad debe ser LEVE, MODERADO o SEVERO")
    private String gravedad;
}
