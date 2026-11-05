package com.odontomax.application.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
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
public class CambioPasswordRequestDTO {

    @JsonProperty("password_actual")
    @NotBlank(message = "La contraseña actual es obligatoria")
    private String passwordActual;

    @JsonProperty("password_nuevo")
    @NotBlank(message = "La nueva contraseña es obligatoria")
    @Size(min = 4, max = 128, message = "La contraseña debe tener al menos 4 caracteres")
    private String passwordNuevo;

    @JsonProperty("passwordActual")
    public void setPasswordActualCamel(String p) {
        if (this.passwordActual == null) this.passwordActual = p;
    }

    @JsonProperty("passwordNuevo")
    public void setPasswordNuevoCamel(String p) {
        if (this.passwordNuevo == null) this.passwordNuevo = p;
    }
}
