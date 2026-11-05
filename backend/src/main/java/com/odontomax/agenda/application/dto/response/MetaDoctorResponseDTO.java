package com.odontomax.agenda.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetaDoctorResponseDTO {
    private UUID especialistaId;
    private BigDecimal metaMensual;
    private BigDecimal comisionExcedentePorcentaje;
}
