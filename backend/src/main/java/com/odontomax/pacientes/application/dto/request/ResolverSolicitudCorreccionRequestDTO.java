package com.odontomax.pacientes.application.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResolverSolicitudCorreccionRequestDTO {

    @Size(max = 500, message = "El comentario no puede superar los 500 caracteres")
    private String comentario;
}
