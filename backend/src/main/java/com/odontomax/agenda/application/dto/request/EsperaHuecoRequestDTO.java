package com.odontomax.agenda.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EsperaHuecoRequestDTO {

    @NotBlank(message = "La sede es obligatoria")
    private String sedeId;

    @NotNull(message = "El paciente es obligatorio")
    private UUID pacienteId;

    /** Doctor preferido; vacío = cualquiera de la sede. */
    private UUID especialistaId;

    @NotBlank(message = "Indica para qué espera el paciente")
    @Size(max = 200, message = "El motivo no puede superar los 200 caracteres")
    private String motivo;

    @Size(max = 500, message = "Las notas no pueden superar los 500 caracteres")
    private String notas;
}
