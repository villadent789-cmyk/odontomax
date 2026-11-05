package com.odontomax.radiologia.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComisionRadiologicaCreateRequestDTO {

    @NotNull(message = "El doctor derivador es obligatorio")
    private UUID doctorDerivadorId;

    @NotBlank(message = "El nombre del paciente es obligatorio")
    private String pacienteNombre;

    private UUID pacienteId;
    private UUID citaId;
    private UUID tratamientoId;

    /**
     * Estudio del catálogo de Villamax: de ahí salen el nombre, el precio y la comisión. Sin él, el tipo de estudio, el
     * precio y el monto de la comisión son obligatorios.
     */
    private String estudioId;

    private String tipoEstudio;

    @DecimalMin(value = "0.00", message = "El precio cobrado debe ser mayor o igual a 0")
    private BigDecimal precioCobrado;

    @DecimalMin(value = "0.00", message = "El monto de comisión debe ser mayor o igual a 0")
    private BigDecimal montoComision;

    /** Número de estudios; nulo = 1. La comisión fija se multiplica por esta cantidad. */
    @Min(value = 1, message = "La cantidad de estudios debe ser al menos 1")
    @Max(value = 200, message = "La cantidad de estudios no puede superar 200")
    private Integer cantidad;

    /** Paciente estudiante con convenio: no genera comisión y el precio cobrado se indica a mano. */
    private Boolean esEstudiante;

    private String nota;

    @Builder.Default
    private String sedeId = "sede-villamax-chilca";
}
