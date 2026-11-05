package com.odontomax.application.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Plan en cuotas: cuota inicial (opcional) y N cuotas mensuales. El costo del plan es la suma de todas. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CuotasPlanRequestDTO {

    @DecimalMin(value = "0.00", message = "La cuota inicial no puede ser negativa")
    private BigDecimal montoInicial;

    @DecimalMin(value = "0.00", message = "La cuota mensual no puede ser negativa")
    private BigDecimal montoMensual;

    @NotNull(message = "La cantidad de cuotas mensuales es obligatoria")
    @Min(value = 0, message = "La cantidad de cuotas mensuales no puede ser negativa")
    @Max(value = 60, message = "El máximo es de 60 cuotas mensuales")
    private Integer cantidadMensuales;

    @NotNull(message = "La fecha del primer vencimiento es obligatoria")
    private LocalDate primerVencimiento;
}
