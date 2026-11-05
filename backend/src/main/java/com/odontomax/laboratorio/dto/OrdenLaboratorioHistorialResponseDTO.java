package com.odontomax.laboratorio.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrdenLaboratorioHistorialResponseDTO {

    private Long id;

    @JsonProperty("orden_id")
    private UUID ordenId;

    @JsonProperty("estado_anterior")
    private String estadoAnterior;

    @JsonProperty("estado_nuevo")
    private String estadoNuevo;

    @JsonProperty("usuario_nombre")
    private String usuarioNombre;

    @JsonProperty("usuario_rol")
    private String usuarioRol;

    private String motivo;

    @JsonProperty("creado_en")
    private OffsetDateTime creadoEn;

    @JsonProperty("ordenId")
    public UUID getOrdenIdCamel() { return ordenId; }

    @JsonProperty("estadoAnterior")
    public String getEstadoAnteriorCamel() { return estadoAnterior; }

    @JsonProperty("estadoNuevo")
    public String getEstadoNuevoCamel() { return estadoNuevo; }

    @JsonProperty("usuarioNombre")
    public String getUsuarioNombreCamel() { return usuarioNombre; }

    @JsonProperty("usuarioRol")
    public String getUsuarioRolCamel() { return usuarioRol; }

    @JsonProperty("creadoEn")
    public OffsetDateTime getCreadoEnCamel() { return creadoEn; }
}
