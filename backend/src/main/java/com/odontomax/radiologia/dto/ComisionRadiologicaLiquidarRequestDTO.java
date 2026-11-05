package com.odontomax.radiologia.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComisionRadiologicaLiquidarRequestDTO {

    @NotBlank(message = "El método de pago es obligatorio (YAPE, PLIN, TRANSFERENCIA, EFECTIVO)")
    private String metodoPago;

    private String codigoOperacion;

    private String nota;
}
