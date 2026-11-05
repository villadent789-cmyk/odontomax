package com.odontomax.pacientes.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SolicitudEliminacionRequestDTO {

    @NotBlank(message = "Indica qué se quiere eliminar")
    @Pattern(regexp = "RADIOGRAFIA|PACIENTE", message = "Solo se puede solicitar eliminar una RADIOGRAFIA o un PACIENTE")
    private String tipo;

    @NotNull(message = "Indica el identificador de lo que se quiere eliminar")
    private UUID objetivoId;

    @NotBlank(message = "Explica el motivo de la eliminación")
    @Size(min = 5, max = 500, message = "El motivo debe tener entre 5 y 500 caracteres")
    private String motivo;
}
