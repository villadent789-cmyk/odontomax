package com.odontomax.agenda.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegistrarContactoRequestDTO {

    @NotBlank(message = "El medio de contacto es obligatorio (WHATSAPP o LLAMADA)")
    private String medio;

    @NotBlank(message = "El resultado del contacto es obligatorio (CONFIRMO, PIDIO_REPROGRAMAR, CANCELO, NO_RESPONDE, MENSAJE_ENVIADO)")
    private String resultado;

    private String notas;
}
