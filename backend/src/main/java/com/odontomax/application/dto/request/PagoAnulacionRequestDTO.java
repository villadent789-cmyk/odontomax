package com.odontomax.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PagoAnulacionRequestDTO {

    @NotBlank(message = "El motivo de anulación es obligatorio para auditoría financiera")
    @com.fasterxml.jackson.annotation.JsonAlias("motivo")
    private String motivoAnulacion;
}
