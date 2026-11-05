package com.odontomax.radiologia.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Estado de cuenta de un doctor que deriva: lo que envió en el periodo, lo que generó y lo pagado y pendiente. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EstadoCuentaDerivadorDTO {

    private UUID doctorId;
    private String doctorNombre;
    private String clinicaOrigen;
    private LocalDate desde;
    private LocalDate hasta;
    private List<Fila> filas;
    private BigDecimal totalGenerado;
    private BigDecimal totalPagado;
    private BigDecimal totalPendiente;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Fila {
        private LocalDate fecha;
        private String pacienteNombre;
        private String estudio;
        private BigDecimal precioCobrado;
        private BigDecimal montoComision;
        /** PENDIENTE o LIQUIDADO. */
        private String estado;
        private LocalDate fechaLiquidacion;
    }
}
