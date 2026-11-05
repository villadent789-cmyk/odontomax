package com.odontomax.application.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OdontogramaPiezaRequestDTO {

    @NotNull(message = "El número de pieza dental FDI es obligatorio.")
    @Min(value = 11, message = "El número de pieza dental FDI no puede ser menor a 11.")
    @Max(value = 85, message = "El número de pieza dental FDI no puede ser mayor a 85.")
    private Integer piezaFdi;

    @NotBlank(message = "El estado o hallazgo dental es obligatorio.")
    private String estado;

    private List<String> caras;

    private String observacion;

    /** Falso para quitar este hallazgo de la pieza; nulo = verdadero. */
    private Boolean activo;

    private UUID especialistaId;
}
