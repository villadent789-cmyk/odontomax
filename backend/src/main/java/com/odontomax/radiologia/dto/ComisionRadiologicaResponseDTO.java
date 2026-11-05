package com.odontomax.radiologia.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComisionRadiologicaResponseDTO {
    private UUID id;
    private UUID doctorDerivadorId;
    private String doctorDerivadorNombre;
    private String doctorDerivadorCop;
    private String doctorDerivadorClinica;
    private String doctorDerivadorTelefono;
    private String doctorDerivadorCuenta;
    private String pacienteNombre;
    private UUID pacienteId;
    private UUID citaId;
    private String tipoEstudio;
    private BigDecimal precioCobrado;
    private Integer cantidad;
    private Boolean esEstudiante;
    private BigDecimal montoComision;
    private String estado;
    private OffsetDateTime fechaEmision;
    private OffsetDateTime fechaLiquidacion;
    private String metodoPago;
    private String codigoOperacion;
    private String nota;
    private String sedeId;
    private String registradoPor;
    private OffsetDateTime creadoEn;
}
