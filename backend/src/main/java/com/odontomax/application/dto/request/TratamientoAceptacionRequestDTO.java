package com.odontomax.application.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TratamientoAceptacionRequestDTO {

    @NotNull(message = "El valor de aceptación es obligatorio")
    private Boolean aceptado;

    private String observaciones;
}
