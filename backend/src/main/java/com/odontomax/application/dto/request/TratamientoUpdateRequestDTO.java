package com.odontomax.application.dto.request;

import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TratamientoUpdateRequestDTO {

    @Pattern(regexp = "PLANIFICADO|EN_PROCESO|COMPLETADO|TERMINADO|INTERRUMPIDO|CANCELADO", message = "Estado inválido")
    private String estado;

    private Integer sesionesRealizadas;

    private LocalDate proximoControl;

    private Boolean requiereSeguimiento;

    private String observaciones;

    private String motivoCierre;

    /** Nuevo número de sesiones del plan; cambiarlo exige un motivo. */
    @jakarta.validation.constraints.Min(value = 1, message = "El plan necesita al menos una sesión")
    @jakarta.validation.constraints.Max(value = 60, message = "Un plan no puede tener más de 60 sesiones")
    private Integer totalSesiones;

    /** Por qué cambia el número de sesiones (por ejemplo: el paciente tuvo que retirarse). */
    @jakarta.validation.constraints.Size(max = 300, message = "El motivo no puede superar los 300 caracteres")
    private String motivoCambioSesiones;
}
