package com.odontomax.promociones.application.dto;

import java.time.LocalDate;
import java.util.List;

public record ReportePromocionesDTO(
        LocalDate desde,
        LocalDate hasta,
        String sedeId,
        long totalEmitidas,
        long totalEnviadas,
        long totalUsadas,
        double tasaUsoPorcentaje,
        List<PromocionCumpleanosDTO> promociones
) {
}
