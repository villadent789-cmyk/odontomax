package com.odontomax.application.dto.request;

import jakarta.validation.constraints.DecimalMin;
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
public class CajaCierreRequestDTO {

    @NotNull(message = "El monto contado en caja física es obligatorio para el arqueo")
    @DecimalMin(value = "0.00", message = "El monto contado no puede ser negativo")
    private BigDecimal montoContado;

    @DecimalMin(value = "0.00", message = "El monto contado de Yape no puede ser negativo")
    private BigDecimal montoContadoYape;

    @DecimalMin(value = "0.00", message = "El monto contado de Plin no puede ser negativo")
    private BigDecimal montoContadoPlin;

    /** Total de tarjetas (débito y crédito juntas) según el cierre del POS. */
    @DecimalMin(value = "0.00", message = "El monto contado de tarjetas no puede ser negativo")
    private BigDecimal montoContadoTarjeta;

    private String observaciones;
}
