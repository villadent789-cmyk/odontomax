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
public class CerrarEpisodioRequestDTO {

    /** Evolución al finalizar el tratamiento (alta odontológica). */
    @Size(max = 500, message = "La nota no puede superar los 500 caracteres")
    private String nota;
}
