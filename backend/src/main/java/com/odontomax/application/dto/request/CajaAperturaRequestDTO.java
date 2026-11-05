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
public class CajaAperturaRequestDTO {

    @NotBlank(message = "La sedeId es obligatoria")
    private String sedeId;

    @NotNull(message = "El monto de apertura es obligatorio")
    @DecimalMin(value = "0.00", message = "El monto de apertura no puede ser negativo")
    @Builder.Default
    private BigDecimal montoApertura = BigDecimal.ZERO;

    private String observaciones;
}
