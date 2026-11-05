package com.odontomax.pacientes.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DescuentoRecomendacionResponseDTO {
    private UUID id;
    /** Paciente nuevo que llegó por la recomendación. */
    private UUID referidoId;
    private String referidoNombre;
    /** PENDIENTE o APLICADO. */
    private String estado;
    private OffsetDateTime creadoEn;
    private OffsetDateTime aplicadoEn;
    private String aplicadoPor;
    private UUID tratamientoId;
}
