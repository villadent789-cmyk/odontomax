package com.odontomax.application.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
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
public class TransferenciaResponseDTO {

    private UUID id;

    @JsonProperty("producto_id")
    private UUID productoId;

    @JsonProperty("producto_nombre")
    private String productoNombre;

    private Integer cantidad;

    @JsonProperty("sede_origen_id")
    private String sedeOrigenId;

    @JsonProperty("sede_origen_nombre")
    private String sedeOrigenNombre;

    @JsonProperty("sede_destino_id")
    private String sedeDestinoId;

    @JsonProperty("sede_destino_nombre")
    private String sedeDestinoNombre;

    private String estado;

    private String motivo;

    @JsonProperty("solicitado_por")
    private UUID solicitadoPor;

    @JsonProperty("usuario_solicita")
    private String usuarioSolicita;

    private String fecha;

    @JsonProperty("creado_en")
    private OffsetDateTime creadoEn;

    @JsonProperty("recibido_en")
    private OffsetDateTime recibidoEn;

    // Dual compatibility getters
    @JsonProperty("productoId")
    public UUID getProductoIdCamel() { return productoId; }

    @JsonProperty("productoNombre")
    public String getProductoNombreCamel() { return productoNombre; }

    @JsonProperty("sedeOrigenId")
    public String getSedeOrigenIdCamel() { return sedeOrigenId; }

    @JsonProperty("sedeDestinoId")
    public String getSedeDestinoIdCamel() { return sedeDestinoId; }

    @JsonProperty("creadoEn")
    public OffsetDateTime getCreadoEnCamel() { return creadoEn; }

    @JsonProperty("recibidoEn")
    public OffsetDateTime getRecibidoEnCamel() { return recibidoEn; }
}
