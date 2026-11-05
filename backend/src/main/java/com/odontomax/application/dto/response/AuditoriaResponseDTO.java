package com.odontomax.application.dto.response;

import com.odontomax.infrastructure.persistence.entity.AuditoriaLogEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditoriaResponseDTO {

    private String id;
    private Instant creadoEn;
    private String fecha;
    private String hora;
    private UUID usuarioId;
    private String usuarioNombre;
    private String rol;
    private String sedeId;
    private String sedeNombre;
    private String modulo;
    private String accion;
    private String tabla;
    private String registroId;
    private String detalle;
    private String ip;

    private static final ZoneId ZONA_PERU = ZoneId.of("America/Lima");
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZONA_PERU);
    private static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZONA_PERU);

    public static AuditoriaResponseDTO fromEntity(AuditoriaLogEntity entity) {
        if (entity == null) return null;
        Instant inst = entity.getCreadoEn() != null ? entity.getCreadoEn() : Instant.now();
        // Las sedes se administran en BD: el frontend resuelve el nombre a partir del id (GET /sedes)
        String sedeNom = (entity.getSedeId() != null && !entity.getSedeId().isBlank()) ? entity.getSedeId() : "General";

        return AuditoriaResponseDTO.builder()
                .id(entity.getId() != null ? entity.getId().toString() : "")
                .creadoEn(inst)
                .fecha(FORMATO_FECHA.format(inst))
                .hora(FORMATO_HORA.format(inst))
                .usuarioId(entity.getUsuarioId())
                .usuarioNombre(entity.getUsuarioNombre() != null ? entity.getUsuarioNombre() : "SISTEMA")
                .rol(entity.getRol() != null ? entity.getRol() : "SISTEMA")
                .sedeId(entity.getSedeId())
                .sedeNombre(sedeNom)
                .modulo(entity.getModulo())
                .accion(entity.getAccion())
                .tabla(entity.getTabla())
                .registroId(entity.getRegistroId())
                .detalle(entity.getDetalle() != null ? entity.getDetalle() : (entity.getAccion() + " en " + entity.getTabla()))
                .ip(entity.getIp())
                .build();
    }
}
