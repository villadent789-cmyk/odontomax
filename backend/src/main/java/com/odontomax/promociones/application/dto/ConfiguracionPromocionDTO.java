package com.odontomax.promociones.application.dto;

import com.odontomax.promociones.domain.model.ConfiguracionPromocion;
import com.odontomax.promociones.domain.model.TipoPromocion;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ConfiguracionPromocionDTO(
        UUID id,
        String sedeId,
        TipoPromocion tipo,
        BigDecimal valor,
        String descripcionBeneficio,
        String plantillaMensaje,
        int diasVigencia,
        boolean activa,
        OffsetDateTime creadoEn,
        OffsetDateTime actualizadoEn) {
    public static ConfiguracionPromocionDTO desdeDominio(ConfiguracionPromocion c) {
        if (c == null)
            return null;
        return new ConfiguracionPromocionDTO(
                c.getId(),
                c.getSedeId(),
                c.getTipo(),
                c.getValor(),
                c.getDescripcionBeneficio(),
                c.getPlantillaMensaje(),
                c.getDiasVigencia(),
                c.isActiva(),
                c.getCreadoEn(),
                c.getActualizadoEn());
    }
}
