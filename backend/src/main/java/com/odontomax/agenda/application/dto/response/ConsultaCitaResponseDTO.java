package com.odontomax.agenda.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsultaCitaResponseDTO {

    private String ticket;
    private String estado;
    private LocalDate fecha;
    private String turno;
    private String sedeId;
    private String sedeNombre;
}
