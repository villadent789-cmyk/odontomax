package com.odontomax.pacientes.application.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AplicarDescuentoRecomendacionRequestDTO {
    /** Plan en el que el doctor dio el descuento. */
    private UUID tratamientoId;
}
