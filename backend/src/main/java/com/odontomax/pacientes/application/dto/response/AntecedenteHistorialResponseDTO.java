package com.odontomax.pacientes.application.dto.response;

import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AntecedenteHistorialResponseDTO {

    private UUID id;
    private UUID pacienteId;
    private String alergias;
    private String antecedentesMedicos;
    private Boolean gestante;
    private Integer semanasGestacion;
    private String motivoCambio;
    private UUID modificadoPor;
    private String modificadoPorNombre;
    private OffsetDateTime creadoEn;
}
