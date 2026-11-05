package com.odontomax.tratamientos.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Un presupuesto vigente (sin aceptar ni rechazar) con el nombre del paciente, para la lista de pendientes. */
@Getter
@Builder
public class PresupuestoPendienteDTO {
    private final UUID id;
    private final UUID pacienteId;
    private final String pacienteNombre;
    private final String especialistaNombre;
    private final String motivo;
    private final BigDecimal total;
    private final int lineas;
    private final Instant creadoEn;
}
