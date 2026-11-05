package com.odontomax.radiologia.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ComisionFallidaResponseDTO {
    private UUID citaId;
    private String pacienteNombre;
    private String estudio;
    private String motivo;
    private Integer intentos;
    private OffsetDateTime creadoEn;
}
