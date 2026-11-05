package com.odontomax.inventario.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Un insumo de la receta de un servicio. En las respuestas vienen también el nombre y la unidad. */
public record RecetaInsumoDTO(
        @NotNull UUID productoId,
        String productoNombre,
        String unidad,
        @NotNull @Min(1) Integer cantidad) {
}
