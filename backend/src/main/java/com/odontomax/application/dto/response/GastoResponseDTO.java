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
public class GastoResponseDTO {

    private UUID id;
    private String sedeId;
    private String sedeNombre;
    private String categoria;
    private String concepto;
    private BigDecimal monto;
    private String metodoPago;
    private String comprobante;
    private String comprobanteKey;
    private String comprobanteUrl;
    private UUID cajaSesionId;
    private Instant gastadoEn;
    private UUID registradoPor;
    private String registradoPorNombre;
    private Boolean anulado;
    private String motivoAnulacion;
    private Instant creadoEn;
}
