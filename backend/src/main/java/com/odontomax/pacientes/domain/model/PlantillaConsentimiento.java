package com.odontomax.pacientes.domain.model;

import java.util.UUID;

public record PlantillaConsentimiento(
        UUID id,
        String codigo,
        String titulo,
        String tipo,
        String contenido,
        Integer version,
        Boolean activo
) {
}