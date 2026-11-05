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
public class TratamientoItemResponseDTO {

    private UUID id;
    private UUID tratamientoId;
    private String procedimientoId;
    private String nombreProcedimiento;
    private Integer piezaFdi;
    private BigDecimal precioBase;
    private Integer cantidad;
    private BigDecimal descuento;
    private BigDecimal precioFinal;
    private String estado;
    private String observaciones;
    private String gravedad;
    private Instant creadoEn;
}
