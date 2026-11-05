package com.odontomax.tratamientos.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PresupuestoCreateRequestDTO {

    @NotNull(message = "El paciente es obligatorio")
    private UUID pacienteId;

    @NotBlank(message = "La sede es obligatoria")
    private String sedeId;

    /** Doctor que presupuesta; nulo = quien lo registra. */
    private UUID especialistaId;

    @Size(max = 300, message = "El motivo no puede superar los 300 caracteres")
    private String motivo;

    @Size(max = 500, message = "Las notas no pueden superar los 500 caracteres")
    private String notas;

    @NotEmpty(message = "El presupuesto necesita al menos un tratamiento")
    @Valid
    private List<Item> items;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private String procedimientoId;

        @NotBlank(message = "El nombre del tratamiento es obligatorio")
        private String nombre;

        private Integer piezaFdi;

        @Pattern(regexp = "LEVE|MODERADO|SEVERO", message = "La gravedad debe ser LEVE, MODERADO o SEVERO")
        private String gravedad;

        @Min(value = 1, message = "La cantidad debe ser al menos 1")
        @Max(value = 200, message = "La cantidad no puede superar 200")
        @Builder.Default
        private Integer cantidad = 1;

        @NotNull(message = "El costo por pieza es obligatorio")
        @DecimalMin(value = "0.00", message = "El costo por pieza no puede ser negativo")
        private BigDecimal precioUnitario;
    }
}
