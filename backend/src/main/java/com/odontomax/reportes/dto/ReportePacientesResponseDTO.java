package com.odontomax.reportes.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportePacientesResponseDTO {

    private String sedeId;
    private String sedeNombre;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private Long totalPacientes;
    private Long pacientesNuevos;
    private Long pacientesRecurrentes;
    private Long pacientesInactivos;

    @Builder.Default
    private List<CaptacionDTO> fuentesCaptacion = new ArrayList<>();

    @Builder.Default
    private List<CumpleaneroDTO> proximosCumpleanos = new ArrayList<>();

    @Builder.Default
    private List<PacientesMesDTO> pacientesPorMes = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CaptacionDTO {
        private String fuente;
        private Long cantidad;
        private Double porcentaje;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CumpleaneroDTO {
        private UUID id;
        private String nombreCompleto;
        private String dni;
        private String telefono;
        private LocalDate fechaNacimiento;
        private Integer edad;
        private String sedeId;
        private String sedeNombre;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PacientesMesDTO {
        private String mes;
        private Long nuevos;
        private Long recurrentes;
    }
}
