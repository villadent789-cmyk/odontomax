package com.odontomax.pacientes.application.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AntecedentesUpdateRequestDTO {

    @Size(max = 1000, message = "Las alergias no pueden superar 1000 caracteres.")
    private String alergias;

    @Size(max = 3000, message = "Los antecedentes médicos no pueden superar 3000 caracteres.")
    private String antecedentesMedicos;

    private Boolean gestante;

    @Min(value = 1, message = "Las semanas de gestación deben ser mínimo 1.")
    @Max(value = 45, message = "Las semanas de gestación deben ser máximo 45.")
    private Integer semanasGestacion;

    @NotBlank(message = "El motivo de la actualización de antecedentes es obligatorio para fines de auditoría médica.")
    @Size(max = 500, message = "El motivo no puede exceder 500 caracteres.")
    private String motivoCambio;
}
