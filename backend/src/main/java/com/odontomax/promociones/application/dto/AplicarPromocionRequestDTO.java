package com.odontomax.promociones.application.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AplicarPromocionRequestDTO {

    @NotNull(message = "El id de la cita donde se aplica es obligatorio")
    private UUID citaId;

    private String notas;
}
