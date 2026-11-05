package com.odontomax.agenda.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HorarioEspecialistaResponseDTO {
    private UUID id;
    private String sedeId;
    private Integer diaSemana;
    private LocalTime horaInicio;
    private LocalTime horaFin;
}
