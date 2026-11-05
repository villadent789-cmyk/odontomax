package com.odontomax.laboratorio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagoLaboratorioResponseDTO {
    private UUID id;
    private UUID ordenId;
    private UUID laboratorioId;
    private String sedeId;
    private BigDecimal monto;
    private String metodoPago;
    private String numeroOperacion;
    private Instant pagadoEn;
    private UUID cajaSesionId;
    private UUID registradoPor;
    private String notas;
    private Boolean anulado;
    private Instant anuladoEn;
    private UUID anuladoPor;
    private String motivoAnulacion;
}
