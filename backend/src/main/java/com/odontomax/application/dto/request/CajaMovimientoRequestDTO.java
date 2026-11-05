package com.odontomax.application.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CajaMovimientoRequestDTO {

    @NotBlank(message = "El tipo de movimiento es obligatorio")
    @Pattern(regexp = "INGRESO|EGRESO", message = "El tipo debe ser INGRESO o EGRESO")
    private String tipo;

    @NotBlank(message = "El concepto del movimiento es obligatorio")
    private String concepto;

    @NotNull(message = "El monto es obligatorio")
    @DecimalMin(value = "0.01", message = "El monto debe ser mayor a cero")
    private BigDecimal monto;

    @Builder.Default
    @Pattern(regexp = "EFECTIVO|YAPE|PLIN|TARJETA_DEBITO|TARJETA_CREDITO", message = "El método de pago debe ser EFECTIVO, YAPE, PLIN, TARJETA_DEBITO o TARJETA_CREDITO")
    private String metodoPago = "EFECTIVO";

    private String referenciaTipo;

    private UUID referenciaId;
}
