package com.odontomax.laboratorio.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LaboratorioExternoRequestDTO {

    @NotBlank(message = "El nombre del laboratorio dental es obligatorio")
    private String nombre;

    private String contacto;
    private String telefono;
    private String email;
    private String direccion;

    @Builder.Default
    private Integer tiempoEntregaPromedioDias = 5;

    @Builder.Default
    private Boolean activo = true;
}
