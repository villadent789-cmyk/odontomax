package com.odontomax.laboratorio.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LaboratorioExternoResponseDTO {

    private UUID id;
    private String nombre;
    private String contacto;
    private String telefono;
    private String email;
    private String direccion;

    @JsonProperty("tiempo_entrega_promedio_dias")
    private Integer tiempoEntregaPromedioDias;

    private boolean activo;

    @JsonProperty("creado_en")
    private OffsetDateTime creadoEn;

    @JsonProperty("actualizado_en")
    private OffsetDateTime actualizadoEn;

    @JsonProperty("tiempoEntregaPromedioDias")
    public Integer getTiempoEntregaPromedioDiasCamel() {
        return tiempoEntregaPromedioDias;
    }

    @JsonProperty("creadoEn")
    public OffsetDateTime getCreadoEnCamel() {
        return creadoEn;
    }

    @JsonProperty("actualizadoEn")
    public OffsetDateTime getActualizadoEnCamel() {
        return actualizadoEn;
    }
}
