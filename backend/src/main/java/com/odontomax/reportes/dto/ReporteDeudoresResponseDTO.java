package com.odontomax.reportes.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReporteDeudoresResponseDTO {

    private String sedeId;
    private String sedeNombre;
    private BigDecimal deudaTotalAcumulada;
    private Long totalPacientesDeudores;

    @Builder.Default
    private List<PacienteDeudorDTO> pacientes = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PacienteDeudorDTO {
        private UUID pacienteId;
        private String pacienteNombre;
        private String dni;
        private String telefono;
        private String sedeId;
        private String sedeNombre;
        private BigDecimal totalTratamientos;
        private BigDecimal totalAbonado;
        private BigDecimal saldoPendiente;
        private Long tratamientosPendientes;
    }
}
