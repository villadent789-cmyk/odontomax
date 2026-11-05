package com.odontomax.radiologia.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DoctorDerivadorRequestDTO {

    @NotBlank(message = "El nombre del doctor derivador es obligatorio")
    private String nombre;

    private String cop;
    private String clinicaOrigen;
    private String telefono;
    private String banco;
    private String numeroCuentaOYape;

    @Builder.Default
    private BigDecimal porcentajeOTarifaFija = BigDecimal.valueOf(10.00);

    @Builder.Default
    private Boolean activo = true;

    private String notas;
}
