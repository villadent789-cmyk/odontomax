package com.odontomax.application.dto.response;

import com.odontomax.infrastructure.persistence.entity.OdontogramaRegistroEntity;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OdontogramaRegistroResponseDTO {

    private UUID id;
    private UUID pacienteId;
    private Integer piezaFdi;
    private String estado;
    private List<String> caras;
    private String observacion;
    private boolean activo;
    private UUID especialistaId;
    private UUID registradoPor;
    private OffsetDateTime registradoEn;

    public static OdontogramaRegistroResponseDTO fromEntity(OdontogramaRegistroEntity entity) {
        if (entity == null) return null;
        return OdontogramaRegistroResponseDTO.builder()
                .id(entity.getId())
                .pacienteId(entity.getPacienteId())
                .piezaFdi(entity.getPiezaFdi())
                .estado(entity.getEstado())
                .caras(entity.getCaras() != null ? Arrays.asList(entity.getCaras()) : List.of())
                .observacion(entity.getObservacion())
                .activo(entity.isActivo())
                .especialistaId(entity.getEspecialistaId())
                .registradoPor(entity.getRegistradoPor())
                .registradoEn(entity.getRegistradoEn())
                .build();
    }
}
