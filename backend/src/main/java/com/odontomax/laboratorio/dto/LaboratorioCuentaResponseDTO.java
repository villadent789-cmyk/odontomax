package com.odontomax.laboratorio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LaboratorioCuentaResponseDTO {
    private UUID laboratorioId;
    private String laboratorioNombre;
    private BigDecimal totalFacturado;
    private BigDecimal totalPagado;
    private BigDecimal saldoPorPagar;
    private List<OrdenLaboratorioCuentaDTO> ordenes;
}
