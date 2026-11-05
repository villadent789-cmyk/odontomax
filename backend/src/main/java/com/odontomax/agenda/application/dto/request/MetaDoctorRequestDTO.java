package com.odontomax.agenda.application.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Meta mensual de cobranza y comisión sobre el excedente. Ambos son opcionales; nulo borra el valor. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetaDoctorRequestDTO {

    @DecimalMin(value = "0.00", message = "La meta mensual no puede ser negativa")
    private BigDecimal metaMensual;

    @DecimalMin(value = "0.00", message = "La comisión no puede ser negativa")
    @DecimalMax(value = "100.00", message = "La comisión no puede superar el 100 %")
    private BigDecimal comisionExcedentePorcentaje;
}
