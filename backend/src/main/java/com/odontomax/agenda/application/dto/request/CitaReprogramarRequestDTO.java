package com.odontomax.agenda.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CitaReprogramarRequestDTO {

    @NotNull(message = "La nueva fecha es obligatoria")
    private LocalDate nuevaFecha;

    @NotNull(message = "El nuevo horario es obligatorio")
    private LocalTime nuevoHorario;

    @NotBlank(message = "El motivo de reprogramación es obligatorio")
    @Size(min = 5, message = "El motivo debe tener al menos 5 caracteres")
    private String motivo;
}
