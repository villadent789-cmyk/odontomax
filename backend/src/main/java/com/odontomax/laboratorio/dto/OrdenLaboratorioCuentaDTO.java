package com.odontomax.laboratorio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrdenLaboratorioCuentaDTO {
    private UUID ordenId;
    private String codigo;
    private String sedeId;
    private BigDecimal costoLaboratorio;
    private BigDecimal montoPagado;
    private BigDecimal saldoLaboratorio;
    private String estadoPago;
}
