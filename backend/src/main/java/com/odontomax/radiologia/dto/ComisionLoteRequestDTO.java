package com.odontomax.radiologia.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/** Liquidar juntas varias comisiones pendientes del mismo doctor con un solo pago. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ComisionLoteRequestDTO {

    @NotEmpty(message = "Elige al menos una comisión")
    @Size(max = 100, message = "Se pueden liquidar hasta 100 comisiones a la vez")
    private List<UUID> comisionIds;

    @NotBlank(message = "El método de pago es obligatorio (EFECTIVO, YAPE o PLIN)")
    private String metodoPago;

    private String codigoOperacion;

    private String nota;
}
