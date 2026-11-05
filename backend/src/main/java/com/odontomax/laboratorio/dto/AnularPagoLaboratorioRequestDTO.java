package com.odontomax.laboratorio.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnularPagoLaboratorioRequestDTO {
    @NotBlank(message = "El motivo de anulación es obligatorio")
    private String motivoAnulacion;
}
