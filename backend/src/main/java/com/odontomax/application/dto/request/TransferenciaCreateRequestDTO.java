package com.odontomax.application.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferenciaCreateRequestDTO {

    @JsonProperty("producto_id")
    @NotNull(message = "El ID del producto es obligatorio")
    private UUID productoId;

    @NotNull(message = "La cantidad es obligatoria")
    @Min(value = 1, message = "La cantidad a transferir debe ser al menos 1")
    private Integer cantidad;

    @JsonProperty("sede_origen_id")
    @NotBlank(message = "La sede de origen es obligatoria")
    private String sedeOrigenId;

    @JsonProperty("sede_destino_id")
    @NotBlank(message = "La sede de destino es obligatoria")
    private String sedeDestinoId;

    private String motivo;
}
