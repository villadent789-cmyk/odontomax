package com.odontomax.reportes.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Rendimiento mensual de los doctores de Villadent: lo cobrado frente a su meta y sus tiempos de atención. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RendimientoDoctoresResponseDTO {

    /** Mes consultado, formato aaaa-mm. */
    private String mes;
    /** Sede consultada; nulo cuando se ven todas las sedes de Villadent. */
    private String sedeId;
    /** Sedes visibles para quien consulta (columnas de la vista "Comparar"). */
    @Builder.Default
    private List<SedeDTO> sedes = new ArrayList<>();
    private BigDecimal totalCobrado;
    @Builder.Default
    private List<DoctorRendimientoDTO> doctores = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SedeDTO {
        private String id;
        private String nombre;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DoctorRendimientoDTO {
        private UUID doctorId;
        private String nombre;
        /** Cobrado en las sedes visibles para quien consulta. */
        private BigDecimal cobrado;
        private Map<String, BigDecimal> cobradoPorSede;
        private BigDecimal metaMensual;
        /** Trabajos de laboratorio del doctor en el mes: no cuentan para la meta, que es por mano de obra. */
        private BigDecimal laboratorioDescontado;
        /** Cobrado total menos laboratorio: lo que se compara con la meta. */
        private BigDecimal cobradoParaMeta;
        /** Cobrado total del doctor en Villadent entre su meta, en porcentaje. Nulo si no tiene meta. */
        private BigDecimal porcentajeAvance;
        /** Solo para el administrador general; nulo si falta meta o porcentaje y para los demás roles. */
        private BigDecimal comisionExcedentePorcentaje;
        private BigDecimal comisionEstimada;
        private long citasAtendidas;
        private long citasCanceladas;
        private long citasNoAsistio;
        private Double duracionRealPromedioMin;
        private Double duracionPrevistaPromedioMin;
        private Double porcentajeCitasQuePasaronDelTiempoPrevisto;
        private Double esperaPromedioMin;
    }
}
