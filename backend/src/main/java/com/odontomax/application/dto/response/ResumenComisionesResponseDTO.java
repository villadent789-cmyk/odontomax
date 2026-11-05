package com.odontomax.application.dto.response;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumenComisionesResponseDTO {
    private BigDecimal totalMontoPendiente;
    private Long cantidadPendientes;
    private BigDecimal totalMontoLiquidado;
    private Long cantidadLiquidados;
    private Long totalDoctoresDerivadoresActivos;
}
