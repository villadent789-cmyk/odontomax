package com.odontomax.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CambioSesionesResponseDTO {
    private int sesionesAntes;
    private int sesionesDespues;
    private String motivo;
    private String usuarioNombre;
    private Instant creadoEn;
}
