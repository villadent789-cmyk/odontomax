package com.odontomax.laboratorio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrdenLaboratorioPruebaResponseDTO {
    private UUID id;
    /** Número de la prueba: 1, 2, 3… */
    private int numero;
    private LocalDate fecha;
    /** NECESITA_AJUSTE o CORRECTO. */
    private String resultado;
    private String notas;
    private String registradoPor;
}
