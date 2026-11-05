package com.odontomax.plantillas.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record RegistroPlantillaRequestDTO(
        @NotNull UUID pacienteId,
        @NotBlank String plantillaCodigo,
        UUID tratamientoId,
        UUID citaId,
        UUID corrigeId,
        @NotNull JsonNode datos) {
}
