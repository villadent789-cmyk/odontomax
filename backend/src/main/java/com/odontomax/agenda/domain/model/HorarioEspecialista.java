package com.odontomax.agenda.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HorarioEspecialista {
    private UUID id;
    private UUID especialistaId;
    private String sedeId;
    private Integer diaSemana;
    private LocalTime horaInicio;
    private LocalTime horaFin;
    private boolean activo;
}
