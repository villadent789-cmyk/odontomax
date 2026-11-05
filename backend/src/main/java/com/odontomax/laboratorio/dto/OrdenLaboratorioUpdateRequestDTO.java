package com.odontomax.laboratorio.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrdenLaboratorioUpdateRequestDTO {

    @NotNull(message = "El laboratorio dental es obligatorio")
    private UUID laboratorioId;

    @NotBlank(message = "El tipo de trabajo de prótesis es obligatorio")
    private String tipoTrabajo;

    private String colorTono;
    private String color;
    private String material;
    private Boolean provisional;

    public String getColorEfectivo() {
        return (color != null && !color.isBlank()) ? color : colorTono;
    }

    private String piezasDentales;
    private LocalDate fechaEstimadaEntrega;
    private BigDecimal costoLaboratorio;
    private BigDecimal precioPaciente;
    private String guiaRemision;
    private String indicacionesTecnicas;
    private String observaciones;
}
