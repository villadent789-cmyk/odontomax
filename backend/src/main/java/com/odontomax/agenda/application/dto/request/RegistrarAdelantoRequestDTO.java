package com.odontomax.agenda.application.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegistrarAdelantoRequestDTO {

    @NotBlank(message = "El medio de pago es obligatorio (YAPE o EFECTIVO)")
    private String medioPago;

    @NotNull(message = "El monto del adelanto es obligatorio")
    @DecimalMin(value = "1.00", message = "El monto debe ser al menos S/ 1.00")
    private BigDecimal monto;

    private String numeroOperacion;

    private String comprobanteUrl;

    private String notas;
}
