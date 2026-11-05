package com.odontomax.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CuotaPlanResponseDTO {
    private Integer numero;
    private String concepto;
    private LocalDate venceEn;
    private BigDecimal monto;
    private BigDecimal pagado;
    private BigDecimal saldo;
    /** PAGADA, PARCIAL, PENDIENTE o VENCIDA. */
    private String estado;
}
