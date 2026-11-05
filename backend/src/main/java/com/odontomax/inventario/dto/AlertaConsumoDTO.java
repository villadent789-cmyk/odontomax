package com.odontomax.inventario.dto;

import java.time.LocalDate;
import java.util.UUID;

/** Una atención que consumió más insumo del que había en stock. */
public record AlertaConsumoDTO(
        UUID id,
        UUID citaId,
        LocalDate fechaCita,
        String motivoCita,
        String sedeId,
        UUID productoId,
        String productoNombre,
        int cantidadRequerida,
        int cantidadDescontada,
        int faltante) {
}
