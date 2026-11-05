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
public class ReporteFinancieroResponseDTO {

    private String sedeId;
    private String sedeNombre;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private BigDecimal totalIngresos;
    private BigDecimal totalGastos;
    private BigDecimal balanceNeto;
    private BigDecimal totalEfectivo;
    private BigDecimal totalYape;
    private BigDecimal totalTarjeta;
    private BigDecimal totalTransferencia;
    private BigDecimal cuentasPorCobrar;
    private Long tratamientosConSaldoPendiente;

    @Builder.Default
    private List<IngresoDiaDTO> ingresosPorDia = new ArrayList<>();

    @Builder.Default
    private List<IngresoMetodoDTO> ingresosPorMetodo = new ArrayList<>();

    @Builder.Default
    private List<IngresoDoctorDTO> ingresosPorDoctor = new ArrayList<>();

    @Builder.Default
    private List<IngresoTratamientoDTO> ingresosPorTratamiento = new ArrayList<>();

    @Builder.Default
    private List<IngresoSedeDTO> ingresosPorSede = new ArrayList<>();

    @Builder.Default
    private List<GastoCategoriaDTO> gastosPorCategoria = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class GastoCategoriaDTO {
        private String categoria;
        private BigDecimal total;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class IngresoDiaDTO {
        private LocalDate fecha;
        private BigDecimal ingresos;
        private BigDecimal gastos;
        private BigDecimal balance;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class IngresoMetodoDTO {
        private String metodo;
        private BigDecimal total;
        private Long cantidad;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class IngresoDoctorDTO {
        private UUID doctorId;
        private String doctorNombre;
        private BigDecimal totalGenerado;
        private Long cantidadTratamientos;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class IngresoTratamientoDTO {
        private String procedimientoNombre;
        private BigDecimal totalIngresos;
        private Long cantidadRealizados;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class IngresoSedeDTO {
        private String sedeId;
        private String sedeNombre;
        private BigDecimal totalIngresos;
        private BigDecimal totalGastos;
        private BigDecimal balance;
    }
}
