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
public class OrdenLaboratorioCreateRequestDTO {

    @NotNull(message = "El paciente es obligatorio")
    private UUID pacienteId;

    @NotNull(message = "El especialista odontólogo es obligatorio")
    private UUID especialistaId;

    @NotBlank(message = "La sede es obligatoria")
    private String sedeId;

    @NotNull(message = "El laboratorio dental es obligatorio")
    private UUID laboratorioId;

    @NotBlank(message = "El tipo de trabajo de prótesis es obligatorio")
    private String tipoTrabajo;

    private String colorTono; // e.g. A1, A2, A3, BL2 (Escala VITA)
    private String color; // alias para pantalla de color dental
    private String material; // Material de confección protésica (Zirconio, Disilicato, etc.)
    private Boolean provisional; // Indicador si es prótesis provisional

    public String getColorEfectivo() {
        return (color != null && !color.isBlank()) ? color : colorTono;
    }

    private String piezasDentales; // e.g. "11, 21", "FDI 2.6"
    private LocalDate fechaEstimadaEntrega;
    private BigDecimal costoLaboratorio;
    private BigDecimal precioPaciente;
    private String guiaRemision;
    private String indicacionesTecnicas;
    private String observaciones;
}
