package com.odontomax.promociones.application.dto;

import com.odontomax.promociones.domain.model.PromocionCumpleanos;
import com.odontomax.promociones.domain.model.TipoPromocion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PromocionCumpleanosDTO(
        UUID id,
        UUID pacienteId,
        String sedeId,
        int anio,
        TipoPromocion tipo,
        BigDecimal valor,
        String beneficio,
        LocalDate fechaEmision,
        LocalDate fechaVencimiento,
        String mensajePersonalizado,
        boolean enviadoWhatsapp,
        OffsetDateTime enviadoEn,
        String enviadoPor,
        boolean usado,
        OffsetDateTime usadoEn,
        UUID citaId,
        String usadoPor,
        String notasUso,
        boolean vigente,
        OffsetDateTime creadoEn
) {
    public static PromocionCumpleanosDTO desdeDominio(PromocionCumpleanos p, LocalDate hoy) {
        if (p == null) return null;
        return new PromocionCumpleanosDTO(
                p.getId(),
                p.getPacienteId(),
                p.getSedeId(),
                p.getAnio(),
                p.getTipo(),
                p.getValor(),
                p.getBeneficio(),
                p.getFechaEmision(),
                p.getFechaVencimiento(),
                p.getMensajePersonalizado(),
                p.isEnviadoWhatsapp(),
                p.getEnviadoEn(),
                p.getEnviadoPor(),
                p.isUsado(),
                p.getUsadoEn(),
                p.getCitaId(),
                p.getUsadoPor(),
                p.getNotasUso(),
                p.esVigente(hoy),
                p.getCreadoEn()
        );
    }
}
