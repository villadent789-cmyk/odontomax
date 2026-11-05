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
public class DoctorDerivadorResponseDTO {
    private UUID id;
    private String nombre;
    private String cop;
    private String clinicaOrigen;
    private String telefono;
    private String banco;
    private String numeroCuentaOYape;
    private BigDecimal porcentajeOTarifaFija;
    private boolean activo;
    private String notas;
    private BigDecimal totalComisionesPendientes;
    private Long cantidadEstudiosDerivados;
    private OffsetDateTime creadoEn;
}
