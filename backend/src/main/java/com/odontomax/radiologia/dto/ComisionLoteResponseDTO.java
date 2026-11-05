package com.odontomax.radiologia.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ComisionLoteResponseDTO {
    private UUID loteId;
    private int cantidad;
    private BigDecimal totalPagado;
}
