package com.odontomax.plantillas.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Ficha llenada por el doctor con una plantilla. BORRADOR se puede editar; CERRADO es inmutable. */
public record RegistroPlantillaDTO(
        UUID id,
        UUID pacienteId,
        UUID tratamientoId,
        UUID citaId,
        String plantillaCodigo,
        String plantillaNombre,
        int plantillaVersion,
        JsonNode datos,
        String estado,
        UUID corrigeId,
        String creadoPorNombre,
        OffsetDateTime creadoEn,
        OffsetDateTime cerradoEn) {
}
