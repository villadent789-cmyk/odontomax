package com.odontomax.pacientes.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsentimientoRequestDTO {

    @NotNull(message = "El ID del paciente es obligatorio.")
    private UUID pacienteId;

    private UUID tratamientoId;

    private UUID plantillaId;

    @NotBlank(message = "El tipo de consentimiento es obligatorio (TRATAMIENTO_DATOS, INFORMADO_PROCEDIMIENTO, USO_IMAGEN).")
    private String tipo;

    @Size(max = 200, message = "El título no puede superar 200 caracteres.")
    private String titulo;

    @NotBlank(message = "El contenido aceptado es obligatorio.")
    private String contenidoAceptado;

    @NotBlank(message = "La firma manuscrita es obligatoria.")
    private String firmaBase64;

    @NotBlank(message = "El nombre del firmante es obligatorio.")
    @Size(max = 150, message = "El nombre del firmante no puede superar 150 caracteres.")
    private String nombreFirmante;

    @NotBlank(message = "El documento de identidad del firmante es obligatorio.")
    @Size(max = 30, message = "El documento no puede superar 30 caracteres.")
    private String documentoFirmante;

    @Size(max = 50, message = "El parentesco no puede superar 50 caracteres.")
    private String parentescoFirmante;
}
