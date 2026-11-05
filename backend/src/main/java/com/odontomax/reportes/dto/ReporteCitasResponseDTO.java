package com.odontomax.reportes.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReporteCitasResponseDTO {

    private String sedeId;
    private String sedeNombre;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private Long totalCitas;
    private Long programadas;
    private Long confirmadas;
    private Long atendidas;
    private Long canceladas;
    private Long noAsistio;
    private Long reprogramadas;
    private Double tasaAsistencia;

    @Builder.Default
    private List<CitasPorDiaDTO> citasPorDia = new ArrayList<>();

    @Builder.Default
    private List<CitasPorEstadoDTO> citasPorEstado = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CitasPorDiaDTO {
        private LocalDate fecha;
        private Long programadas;
        private Long atendidas;
        private Long canceladas;
        private Long noAsistio;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CitasPorEstadoDTO {
        private String estado;
        private Long cantidad;
        private Double porcentaje;
    }
}
