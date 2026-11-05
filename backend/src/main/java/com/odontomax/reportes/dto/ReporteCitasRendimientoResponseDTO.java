package com.odontomax.reportes.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReporteCitasRendimientoResponseDTO {

    private String sedeId;
    private String sedeNombre;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private Long totalCitas;
    private Long citasAtendidas;
    private Long citasCanceladas;
    private Long citasNoAsistio;
    private Long citasProgramadas;
    private Double tasaAsistencia; // porcentaje 0-100
}
