package com.odontomax.application.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TotpLoginRequestDTO {

    @JsonProperty("temp_token")
    @NotBlank(message = "El token temporal de autenticación es obligatorio")
    private String tempToken;

    @NotBlank(message = "El código TOTP o código de respaldo es obligatorio")
    private String codigo;

    @JsonProperty("tempToken")
    public void setTempTokenCamel(String t) {
        if (this.tempToken == null) this.tempToken = t;
    }
}
