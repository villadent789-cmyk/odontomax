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
public class PagoResponseDTO {

    private UUID id;
    private String sedeId;
    private String sedeNombre;
    private UUID pacienteId;
    private String pacienteNombre;
    private String pacienteDni;
    private UUID tratamientoId;
    private String tratamientoNombre;
    private BigDecimal monto;
    private String metodoPago;
    private String numeroOperacion;
    private String comprobante;
    private String comprobanteTipo;
    private String comprobanteSerie;
    private String comprobanteNumero;
    private String comprobanteHash;
    private UUID cajaSesionId;
    private UUID especialistaId;
    private String especialistaNombre;
    private Instant pagadoEn;
    private UUID registradoPor;
    private String registradoPorNombre;
    private Boolean anulado;
    private Instant anuladoEn;
    private UUID anuladoPor;
    private String anuladoPorNombre;
    private String motivoAnulacion;
    private Instant creadoEn;
}
