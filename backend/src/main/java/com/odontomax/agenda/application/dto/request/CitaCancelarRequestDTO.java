package com.odontomax.agenda.application.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CitaCancelarRequestDTO {

    @jakarta.validation.constraints.NotBlank(message = "El motivo de cancelación es obligatorio")
    @Size(min = 5, max = 500, message = "El motivo debe tener entre 5 y 500 caracteres")
    private String motivo;

    /**
     * Permite a un administrador autorizar saldo a favor aun si la cancelación es con menos de 24 horas de anticipación.
     * Esta excepción queda registrada y auditada en el sistema.
     */
    private Boolean excepcionAdministrador;

    public CitaCancelarRequestDTO(String motivo) {
        this.motivo = motivo;
        this.excepcionAdministrador = false;
    }
}
