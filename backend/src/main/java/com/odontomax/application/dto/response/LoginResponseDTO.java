package com.odontomax.application.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponseDTO {
    private String token;
    @Builder.Default
    private String tokenTipo = "Bearer";
    private long expiracionMs;
    private UsuarioDTO usuario;

    /**
     * Solo presente (true) si el rol exige 2FA y la cuenta no lo tiene configurado: el cliente debe llevar al usuario
     * a configurarlo (/auth/2fa/configurar y /auth/2fa/verificar-activar). Ausente en el resto de logins.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Boolean requiereConfigurar2fa;
}
