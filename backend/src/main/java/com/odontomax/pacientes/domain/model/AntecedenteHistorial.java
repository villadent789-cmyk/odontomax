package com.odontomax.pacientes.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Instantanea historica inmutable de antecedentes medicos y alergias de un paciente.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AntecedenteHistorial {
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