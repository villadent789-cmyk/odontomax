package com.odontomax.plantillas.dto;

import com.fasterxml.jackson.databind.JsonNode;

/** Plantilla clínica de una familia de tratamientos: qué datos se registran y en qué secciones. */
public record PlantillaTratamientoDTO(
        String codigo,
        String nombre,
        String negocio,
        String descripcion,
        int version,
        JsonNode esquema) {
}
