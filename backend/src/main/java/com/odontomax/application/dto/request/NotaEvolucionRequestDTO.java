package com.odontomax.application.dto.request;

import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotaEvolucionRequestDTO {

    private UUID citaId;

    @Builder.Default
    private String tipoNota = "SOAP";

    @Size(max = 2000, message = "El campo subjetivo no puede exceder 2000 caracteres.")
    private String subjetivo;

    @Size(max = 2000, message = "El campo objetivo no puede exceder 2000 caracteres.")
    private String objetivo;

    @Size(max = 2000, message = "El campo análisis no puede exceder 2000 caracteres.")
    private String analisis;

    @Size(max = 2000, message = "El campo plan no puede exceder 2000 caracteres.")
    private String plan;

    @Size(max = 5000, message = "El contenido no puede exceder 5000 caracteres.")
    private String contenido;

    private UUID notaReferenciaId;

    @Size(max = 500, message = "El motivo de corrección no puede exceder 500 caracteres.")
    private String motivoCorreccion;
}
