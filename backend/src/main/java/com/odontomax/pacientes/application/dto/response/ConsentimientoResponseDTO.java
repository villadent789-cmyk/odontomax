package com.odontomax.pacientes.application.dto.response;

import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsentimientoResponseDTO {

    private UUID id;
    private UUID pacienteId;
    private UUID tratamientoId;
    private String tipo;
    private UUID plantillaId;
    private String titulo;
    private String contenidoAceptado;
    private String firmaUrl;
    private String pdfUrl;
    private String nombreFirmante;
    private String documentoFirmante;
    private String parentescoFirmante;
    private OffsetDateTime firmadoEn;
}
