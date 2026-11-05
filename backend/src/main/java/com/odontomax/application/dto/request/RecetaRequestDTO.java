package com.odontomax.application.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecetaRequestDTO {

    private UUID pacienteId;

    @NotBlank(message = "El nombre del paciente es obligatorio")
    private String pacienteNombre;

    private String pacienteDni;
    private Integer pacienteEdad;

    @NotBlank(message = "El nombre del profesional tratante es obligatorio")
    private String doctorNombre;

    private String doctorCop;
    private String sedeNombre;

    @Builder.Default
    private LocalDate fecha = LocalDate.now();

    private String diagnostico;

    @NotEmpty(message = "Debe incluir al menos un medicamento o indicación")
    @Valid
    private List<ItemRecetaDTO> medicamentos;

    private String indicacionesGenerales;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemRecetaDTO {
        @NotBlank(message = "El nombre del fármaco es obligatorio")
        private String medicamento; // ej: "Amoxicilina 500mg"
        private String dosis; // ej: "1 cápsula"
        private String frecuencia; // ej: "Cada 8 horas"
        private String duracion; // ej: "Por 7 días"
        private String indicaciones; // ej: "Tomar con alimentos"
    }
}
