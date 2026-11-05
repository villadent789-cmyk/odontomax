package com.odontomax.application.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Código del segundo factor: TOTP de 6 dígitos o código de respaldo (cuerpo opcional de /auth/2fa/configurar). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TotpCodigoRequestDTO {

    private String codigo;
}
