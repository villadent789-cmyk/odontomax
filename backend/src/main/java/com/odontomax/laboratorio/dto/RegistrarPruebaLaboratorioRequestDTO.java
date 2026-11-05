package com.odontomax.laboratorio.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegistrarPruebaLaboratorioRequestDTO {

    @NotBlank(message = "El resultado de la prueba es obligatorio")
    @Pattern(regexp = "NECESITA_AJUSTE|CORRECTO", message = "El resultado debe ser NECESITA_AJUSTE o CORRECTO")
    private String resultado;

    @Size(max = 500, message = "Las notas no pueden superar los 500 caracteres")
    private String notas;

    /** Fecha de la prueba; nula = hoy. */
    private LocalDate fecha;

    /** Si necesita ajuste: nueva fecha estimada de entrega del laboratorio. */
    private LocalDate nuevaFechaEntrega;
}
