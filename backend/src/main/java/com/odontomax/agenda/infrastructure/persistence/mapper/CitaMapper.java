package com.odontomax.agenda.infrastructure.persistence.mapper;

import com.odontomax.agenda.domain.model.Cita;
import com.odontomax.agenda.infrastructure.persistence.entity.CitaEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class CitaMapper {

    public Cita toDomain(CitaEntity entity) {
        if (entity == null) return null;
        return Cita.builder()
                .id(entity.getId())
                .pacienteId(entity.getPacienteId())
                .pacienteNombre(entity.getPacienteNombre())
                .pacienteTelefono(entity.getPacienteTelefono())
                .doctorId(entity.getDoctorId())
                .doctorNombre(entity.getDoctorNombre())
                .sedeId(entity.getSedeId())
                .sillonNumero(entity.getSillonNumero())
                .motivoConsulta(entity.getMotivoConsulta())
                .fecha(entity.getFecha())
                .hora(entity.getHora())
                .duracionMinutos(entity.getDuracionMinutos() != null ? entity.getDuracionMinutos() : 30)
                .estado(entity.getEstado())
                .llegadaEn(entity.getLlegadaEn())
                .inicioAtencionEn(entity.getInicioAtencionEn())
                .finAtencionEn(entity.getFinAtencionEn())
                .motivoCancelacion(entity.getMotivoCancelacion())
                .motivoCancelacionPor(entity.getMotivoCancelacionPor())
                .canceladaEn(entity.getCanceladaEn())
                .motivoReprogramacion(entity.getMotivoReprogramacion())
                .motivoReprogramacionPor(entity.getMotivoReprogramacionPor())
                .reprogramadaEn(entity.getReprogramadaEn())
                .notas(entity.getNotas())
                .codigoTicket(entity.getCodigoTicket())
                .adelantoRequerido(Boolean.TRUE.equals(entity.getAdelantoRequerido()))
                .adelantoMonto(entity.getAdelantoMonto() != null ? entity.getAdelantoMonto() : BigDecimal.ZERO)
                .adelantoEstado(entity.getAdelantoEstado() != null ? entity.getAdelantoEstado() : "NO_APLICA")
                .adelantoMedioPago(entity.getAdelantoMedioPago())
                .adelantoNumeroOperacion(entity.getAdelantoNumeroOperacion())
                .adelantoComprobanteUrl(entity.getAdelantoComprobanteUrl())
                .adelantoRegistradoPor(entity.getAdelantoRegistradoPor())
                .adelantoRegistradoEn(entity.getAdelantoRegistradoEn())
                .adelantoLimitePago(entity.getAdelantoLimitePago())
                .estadoConfirmacion(entity.getEstadoConfirmacion() != null ? entity.getEstadoConfirmacion() : "SIN_CONTACTAR")
                .ultimoContactoMedio(entity.getUltimoContactoMedio())
                .ultimoContactoFecha(entity.getUltimoContactoFecha())
                .ultimoContactoUsuario(entity.getUltimoContactoUsuario())
                .ultimoContactoResultado(entity.getUltimoContactoResultado())
                .tratamientoId(entity.getTratamientoId())
                .doctorDerivadorId(entity.getDoctorDerivadorId())
                .pacienteDirecto(Boolean.TRUE.equals(entity.getPacienteDirecto()))
                .build();
    }

    public CitaEntity toEntity(Cita domain) {
        if (domain == null) return null;
        return CitaEntity.builder()
                .id(domain.getId())
                .pacienteId(domain.getPacienteId())
                .pacienteNombre(domain.getPacienteNombre())
                .pacienteTelefono(domain.getPacienteTelefono())
                .doctorId(domain.getDoctorId())
                .doctorNombre(domain.getDoctorNombre())
                .sedeId(domain.getSedeId())
                .sillonNumero(domain.getSillonNumero())
                .motivoConsulta(domain.getMotivoConsulta())
                .fecha(domain.getFecha())
                .hora(domain.getHora())
                .duracionMinutos(domain.getDuracionMinutos())
                .estado(domain.getEstado())
                .llegadaEn(domain.getLlegadaEn())
                .inicioAtencionEn(domain.getInicioAtencionEn())
                .finAtencionEn(domain.getFinAtencionEn())
                .motivoCancelacion(domain.getMotivoCancelacion())
                .motivoCancelacionPor(domain.getMotivoCancelacionPor())
                .canceladaEn(domain.getCanceladaEn())
                .motivoReprogramacion(domain.getMotivoReprogramacion())
                .motivoReprogramacionPor(domain.getMotivoReprogramacionPor())
                .reprogramadaEn(domain.getReprogramadaEn())
                .notas(domain.getNotas())
                .codigoTicket(domain.getCodigoTicket())
                .adelantoRequerido(domain.isAdelantoRequerido())
                .adelantoMonto(domain.getAdelantoMonto())
                .adelantoEstado(domain.getAdelantoEstado())
                .adelantoMedioPago(domain.getAdelantoMedioPago())
                .adelantoNumeroOperacion(domain.getAdelantoNumeroOperacion())
                .adelantoComprobanteUrl(domain.getAdelantoComprobanteUrl())
                .adelantoRegistradoPor(domain.getAdelantoRegistradoPor())
                .adelantoRegistradoEn(domain.getAdelantoRegistradoEn())
                .adelantoLimitePago(domain.getAdelantoLimitePago())
                .estadoConfirmacion(domain.getEstadoConfirmacion())
                .ultimoContactoMedio(domain.getUltimoContactoMedio())
                .ultimoContactoFecha(domain.getUltimoContactoFecha())
                .ultimoContactoUsuario(domain.getUltimoContactoUsuario())
                .ultimoContactoResultado(domain.getUltimoContactoResultado())
                .tratamientoId(domain.getTratamientoId())
                .doctorDerivadorId(domain.getDoctorDerivadorId())
                .pacienteDirecto(domain.isPacienteDirecto())
                .build();
    }
}
