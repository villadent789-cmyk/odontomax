package com.odontomax.reportes.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReporteDoctoresResponseDTO {

    private String sedeId;
    private String sedeNombre;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;

    @Builder.Default
    private List<DoctorRendimientoDTO> doctores = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DoctorRendimientoDTO {
        private UUID doctorId;
        private String nombre;
        private String especialidad;
        private Long citasAtendidas;
        private Long citasCanceladas;
        private Long citasNoAsistio;
        private Long tratamientosRealizados;
        private BigDecimal ingresosGenerados;
        private BigDecimal ticketPromedio;
        private Double tasaEfectividad;
    }
}
