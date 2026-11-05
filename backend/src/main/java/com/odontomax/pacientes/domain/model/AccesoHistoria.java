package com.odontomax.pacientes.domain.model;

import com.odontomax.identidad.api.ClinicaUserPrincipal;
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
public class AccesoHistoria {

    public static final int VENTANA_DEDUPLICACION_MINUTOS = 5;

    private UUID id;
    private UUID pacienteId;
    private UUID usuarioId;
    private String usuarioNombre;
    private String usuarioEmail;
    private String usuarioRol;
    private String sedeId;
    private String ip;
    private OffsetDateTime accedidoEn;

    public static AccesoHistoria registrar(UUID pacienteId, ClinicaUserPrincipal user, String sedeIdPaciente, String ip) {
        return AccesoHistoria.builder()
                .id(UUID.randomUUID())
                .pacienteId(pacienteId)
                .usuarioId(user != null ? user.id() : UUID.randomUUID())
                .usuarioNombre(user != null ? user.nombre() : "Usuario Desconocido")
                .usuarioEmail(user != null ? user.email() : "desconocido@odontomax.pe")
                .usuarioRol(user != null ? user.rol() : "ANONYMOUS")
                .sedeId(sedeIdPaciente)
                .ip(ip != null && !ip.isBlank() ? ip : "127.0.0.1")
                .accedidoEn(OffsetDateTime.now())
                .build();
    }
}