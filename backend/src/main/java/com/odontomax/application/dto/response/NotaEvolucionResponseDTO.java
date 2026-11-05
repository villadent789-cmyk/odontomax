package com.odontomax.application.dto.response;

import com.odontomax.infrastructure.persistence.entity.NotaEvolucionEntity;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotaEvolucionResponseDTO {

    private UUID id;
    private UUID pacienteId;
    private UUID citaId;
    private UUID autorId;
    private String autorNombre;
    private String autorRol;
    private String tipoNota;
    private String subjetivo;
    private String objetivo;
    private String analisis;
    private String plan;
    private String contenido;
    private UUID notaReferenciaId;
    private String motivoCorreccion;
    private Boolean firmado;
    private OffsetDateTime creadoEn;

    public static NotaEvolucionResponseDTO fromEntity(NotaEvolucionEntity entity) {
        if (entity == null) return null;
        return NotaEvolucionResponseDTO.builder()
                .id(entity.getId())
                .pacienteId(entity.getPacienteId())
                .citaId(entity.getCitaId())
                .autorId(entity.getAutorId())
                .autorNombre(entity.getAutorNombre())
                .autorRol(entity.getAutorRol())
                .tipoNota(entity.getTipoNota())
                .subjetivo(entity.getSubjetivo())
                .objetivo(entity.getObjetivo())
                .analisis(entity.getAnalisis())
                .plan(entity.getPlan())
                .contenido(entity.getContenido())
                .notaReferenciaId(entity.getNotaReferenciaId())
                .motivoCorreccion(entity.getMotivoCorreccion())
                .firmado(entity.getFirmado())
                .creadoEn(entity.getCreadoEn())
                .build();
    }
}
