package com.odontomax.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Radiografia {

    private UUID id;
    private UUID pacienteId;
    private UUID doctorId;

    // Categoría: PANORAMICA, PERIAPICAL, ALETA_MORDIDA, CEFALOMETRICA, FOTO_ANTES, FOTO_DESPUES
    private String tipoEstudio;
    private Integer piezaFdi; // Opcional si es periapical (ej. 46)
    private String etapa; // "INICIAL", "CONTROL", "FINAL"

    // URL almacenada en Cloudflare R2
    private String archivoUrl;
    private String nombreArchivo;
    private long tamanoBytes;

    private String diagnosticoRadiografico;
    private LocalDate fechaToma;
}
