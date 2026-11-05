package com.odontomax.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioResetPasswordDTO {

    @NotBlank(message = "La nueva contraseña es obligatoria")
    @Size(min = 4, max = 128, message = "La contraseña debe tener al menos 4 caracteres")
    private String newPassword;
}
