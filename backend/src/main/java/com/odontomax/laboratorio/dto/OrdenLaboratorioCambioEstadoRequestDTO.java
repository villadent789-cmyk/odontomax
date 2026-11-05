package com.odontomax.laboratorio.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrdenLaboratorioCambioEstadoRequestDTO {

    @NotBlank(message = "El nuevo estado es obligatorio")
    private String nuevoEstado;

    private String motivo;
    private String guiaRemision;
}
