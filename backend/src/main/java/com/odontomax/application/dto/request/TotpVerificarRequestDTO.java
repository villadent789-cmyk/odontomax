package com.odontomax.application.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TotpVerificarRequestDTO {

    @NotNull(message = "El código TOTP de 6 dígitos es obligatorio")
    private Integer codigo;
}
