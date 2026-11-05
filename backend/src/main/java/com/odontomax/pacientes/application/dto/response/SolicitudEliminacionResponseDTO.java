package com.odontomax.pacientes.application.dto.response;

import com.odontomax.pacientes.domain.model.SolicitudEliminacion;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class SolicitudEliminacionResponseDTO {
    private UUID id;
    private String tipo;
    private UUID objetivoId;
    private UUID pacienteId;
    private String sedeId;
    private String descripcion;
    private String motivo;
    private String estado;
    private String solicitadoPorNombre;
    private OffsetDateTime solicitadoEn;
    private String resueltoPorNombre;
    private OffsetDateTime resueltoEn;
    private String comentarioResolucion;

    public static SolicitudEliminacionResponseDTO from(SolicitudEliminacion s) {
        return SolicitudEliminacionResponseDTO.builder()
                .id(s.getId()).tipo(s.getTipo()).objetivoId(s.getObjetivoId()).pacienteId(s.getPacienteId())
                .sedeId(s.getSedeId()).descripcion(s.getDescripcion()).motivo(s.getMotivo()).estado(s.getEstado())
                .solicitadoPorNombre(s.getSolicitadoPorNombre()).solicitadoEn(s.getSolicitadoEn())
                .resueltoPorNombre(s.getResueltoPorNombre()).resueltoEn(s.getResueltoEn())
                .comentarioResolucion(s.getComentarioResolucion())
                .build();
    }
}
