package com.odontomax.pacientes.application.dto.response;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlantillaConsentimientoResponseDTO {

    private UUID id;
    private String codigo;
    private String titulo;
    private String tipo;
    private String contenido;
    private Integer version;
    private Boolean activo;
}
