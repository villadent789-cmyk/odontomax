package com.odontomax.reportes.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/** Avance propio de un odontólogo en el mes: su cobrado frente a su meta y sus tiempos. Nunca incluye comisión. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MiAvanceResponseDTO {
    private String mes;
    private UUID doctorId;
    private String nombre;
    private BigDecimal cobrado;
    private BigDecimal metaMensual;
    /** Trabajos de laboratorio del mes: no cuentan para la meta, que es por mano de obra. */
    private BigDecimal laboratorioDescontado;
    /** Cobrado menos laboratorio: lo que se compara con la meta. */
    private BigDecimal cobradoParaMeta;
    private BigDecimal porcentajeAvance;
    private long citasAtendidas;
    private Double duracionRealPromedioMin;
    private Double duracionPrevistaPromedioMin;
    private Double porcentajeCitasQuePasaronDelTiempoPrevisto;
    private Double esperaPromedioMin;
}
