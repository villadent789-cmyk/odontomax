package com.odontomax.clinica.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AbrirEpisodioRequestDTO {

    /** Por qué viene el paciente: dolor de muela, tratamiento, control a los 3 meses… */
    @Size(max = 300, message = "El motivo no puede superar los 300 caracteres")
    private String motivo;
}
