package com.odontomax.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CajaMovimientoResponseDTO {

    private UUID id;
    private UUID sesionId;
    private String tipo;
    private String concepto;
    private BigDecimal monto;
    private String metodoPago;
    private String referenciaTipo;
    private UUID referenciaId;
    private UUID registradoPor;
    private String registradoPorNombre;
    private Instant registradoEn;
}
