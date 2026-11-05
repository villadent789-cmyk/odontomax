package com.odontomax.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Ortodoncia {

    private UUID id;
    private UUID pacienteId;
    private UUID doctorId;
    private String sedeId;

    private String tipoBrackets; // "METALICOS", "ESTETICOS_ZAFIRO", "AUTOLIGADOS", "ALINEADORES"
    private LocalDate fechaInicio;
    private BigDecimal presupuestoTotal;
    private BigDecimal pagoMensualidad;

    private String estado; // "ACTIVO", "PAUSADO", "FINALIZADO"
    private List<ControlOrtodoncia> controles;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ControlOrtodoncia {
        private UUID id;
        private LocalDate fecha;
        private String arcoSuperior; // ej: "NiTi 0.014"
        private String arcoInferior; // ej: "Acero 0.016x0.022"
        private String ligaduras;
        private String nivelHigiene; // "BUENA", "REGULAR", "DEFICIENTE"
        private String observaciones;
        private BigDecimal pagoAbonado;
    }
}
