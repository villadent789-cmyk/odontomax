package com.odontomax.plantillas.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

public record DatosRegistroRequestDTO(@NotNull JsonNode datos) {
}
