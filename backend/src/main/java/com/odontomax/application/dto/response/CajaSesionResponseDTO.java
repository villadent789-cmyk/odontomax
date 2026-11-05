package com.odontomax.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CajaSesionResponseDTO {

    private UUID id;
    private String sedeId;
    private String sedeNombre;
    private UUID abiertaPor;
    private String abiertaPorNombre;
    private Instant abiertaEn;
    private BigDecimal montoApertura;
    private UUID cerradaPor;
    private String cerradaPorNombre;
    private Instant cerradaEn;
    private BigDecimal montoEsperado;
    private BigDecimal montoContado;
    private BigDecimal diferencia;
    private BigDecimal totalIngresosEfectivo;
    private BigDecimal totalEgresosEfectivo;
    private BigDecimal totalYape;
    private BigDecimal montoEsperadoYape;
    private BigDecimal montoContadoYape;
    private BigDecimal diferenciaYape;
    private BigDecimal montoEsperadoPlin;
    private BigDecimal montoContadoPlin;
    private BigDecimal diferenciaPlin;
    private BigDecimal totalIngresosYape;
    private BigDecimal totalEgresosYape;
    private BigDecimal totalIngresosPlin;
    private BigDecimal totalEgresosPlin;
    private BigDecimal montoEsperadoTarjeta;
    private BigDecimal montoContadoTarjeta;
    private BigDecimal diferenciaTarjeta;
    private BigDecimal totalIngresosTarjetaDebito;
    private BigDecimal totalIngresosTarjetaCredito;
    private BigDecimal totalEgresosTarjeta;
    private String estado; // "ABIERTA", "CERRADA"
    private String observaciones;

    @Builder.Default
    private List<CajaMovimientoResponseDTO> movimientos = new ArrayList<>();
}
