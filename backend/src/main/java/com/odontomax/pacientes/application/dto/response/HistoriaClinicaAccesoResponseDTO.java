package com.odontomax.pacientes.application.dto.response;

import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistoriaClinicaAccesoResponseDTO {

    private UUID id;
    private UUID pacienteId;
    private UUID usuarioId;
    private String usuarioNombre;
    private String usuarioEmail;
    private String usuarioRol;
    private String sedeId;
    private String ip;
    private OffsetDateTime accedidoEn;
}
