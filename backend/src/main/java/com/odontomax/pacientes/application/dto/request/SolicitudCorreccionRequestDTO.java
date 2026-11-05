package com.odontomax.pacientes.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/** Datos que el solicitante quiere corregir; los que no se envíen se quedan como están. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SolicitudCorreccionRequestDTO {

    @Size(max = 120, message = "Los nombres no pueden superar los 120 caracteres")
    private String nombres;

    @Size(max = 120, message = "Los apellidos no pueden superar los 120 caracteres")
    private String apellidos;

    @Pattern(regexp = "^([0-9]{8})?$", message = "El DNI debe tener 8 dígitos")
    private String dni;

    private LocalDate fechaNacimiento;

    @NotBlank(message = "Explica el motivo de la corrección")
    @Size(max = 500, message = "El motivo no puede superar los 500 caracteres")
    private String motivo;
}
