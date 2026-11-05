package com.odontomax.application.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** POST /auth/2fa/desactivar: contraseña actual y un código del 2FA activo (TOTP de 6 dígitos o de respaldo). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Desactivar2faRequestDTO {

    @JsonProperty("password_actual")
    @NotBlank(message = "La contraseña actual es obligatoria")
    private String passwordActual;

    @NotBlank(message = "El código TOTP o código de respaldo es obligatorio")
    private String codigo;

    @JsonProperty("passwordActual")
    public void setPasswordActualCamel(String p) {
        if (this.passwordActual == null) this.passwordActual = p;
    }
}
