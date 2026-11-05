package com.odontomax.agenda.application.dto.response;

import com.odontomax.agenda.domain.model.Cita;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CitaResponseDTO {

    private UUID id;
    private UUID pacienteId;
    /** Inasistencias acumuladas del paciente; la agenda marca a quien tiene 2 o más. */
    private Integer pacienteInasistencias;
    private String pacienteNombre;
    private String pacienteTelefono;
    private UUID doctorId;
    private String doctorNombre;
    private String sedeId;
    private Integer sillonNumero;
    private String motivoConsulta;
    private LocalDate fecha;
    private LocalTime hora;
    private int duracionMinutos;
    private String estado;
    private Instant llegadaEn;
    private Instant inicioAtencionEn;
    private Instant finAtencionEn;
    private String motivoCancelacion;
    private String motivoCancelacionPor;
    private Instant canceladaEn;
    private String motivoReprogramacion;
    private String motivoReprogramacionPor;
    private Instant reprogramadaEn;
    private String notas;
    private String codigoTicket;

    // Gestión de adelantos v2
    private boolean adelantoRequerido;
    private BigDecimal adelantoMonto;
    private String adelantoEstado;
    private String adelantoMedioPago;
    private String adelantoNumeroOperacion;
    private String adelantoComprobanteUrl;
    private String adelantoRegistradoPor;
    private OffsetDateTime adelantoRegistradoEn;
    private OffsetDateTime adelantoLimitePago;
    private Long minutosParaExpirarAdelanto;

    // Trazabilidad de contactos y confirmaciones v2
    private String estadoConfirmacion;
    private String ultimoContactoMedio;
    private OffsetDateTime ultimoContactoFecha;
    private String ultimoContactoUsuario;
    private String ultimoContactoResultado;

    /** Plan de tratamiento al que corresponde la cita, si es una sesión de un plan. */
    private UUID tratamientoId;
    private UUID doctorDerivadorId;
    private Boolean pacienteDirecto;

    public static CitaResponseDTO fromDomain(Cita c) {
        if (c == null) return null;

        Long minutosRestantes = null;
        if ("PENDIENTE_DE_PAGO".equalsIgnoreCase(c.getEstado()) && c.getAdelantoLimitePago() != null) {
            long rest = Duration.between(OffsetDateTime.now(), c.getAdelantoLimitePago()).toMinutes();
            minutosRestantes = Math.max(0, rest);
        }

        return CitaResponseDTO.builder()
                .id(c.getId())
                .pacienteId(c.getPacienteId())
                .pacienteNombre(c.getPacienteNombre())
                .pacienteTelefono(c.getPacienteTelefono())
                .doctorId(c.getDoctorId())
                .doctorNombre(c.getDoctorNombre())
                .sedeId(c.getSedeId())
                .sillonNumero(c.getSillonNumero())
                .motivoConsulta(c.getMotivoConsulta())
                .fecha(c.getFecha())
                .hora(c.getHora())
                .duracionMinutos(c.getDuracionMinutos())
                .estado(c.getEstado())
                .llegadaEn(c.getLlegadaEn())
                .inicioAtencionEn(c.getInicioAtencionEn())
                .finAtencionEn(c.getFinAtencionEn())
                .motivoCancelacion(c.getMotivoCancelacion())
                .motivoCancelacionPor(c.getMotivoCancelacionPor())
                .canceladaEn(c.getCanceladaEn())
                .motivoReprogramacion(c.getMotivoReprogramacion())
                .motivoReprogramacionPor(c.getMotivoReprogramacionPor())
                .reprogramadaEn(c.getReprogramadaEn())
                .notas(c.getNotas())
                .codigoTicket(c.getCodigoTicket())
                .adelantoRequerido(c.isAdelantoRequerido())
                .adelantoMonto(c.getAdelantoMonto())
                .adelantoEstado(c.getAdelantoEstado())
                .adelantoMedioPago(c.getAdelantoMedioPago())
                .adelantoNumeroOperacion(c.getAdelantoNumeroOperacion())
                .adelantoComprobanteUrl(c.getAdelantoComprobanteUrl())
                .adelantoRegistradoPor(c.getAdelantoRegistradoPor())
                .adelantoRegistradoEn(c.getAdelantoRegistradoEn())
                .adelantoLimitePago(c.getAdelantoLimitePago())
                .minutosParaExpirarAdelanto(minutosRestantes)
                .estadoConfirmacion(c.getEstadoConfirmacion())
                .ultimoContactoMedio(c.getUltimoContactoMedio())
                .ultimoContactoFecha(c.getUltimoContactoFecha())
                .ultimoContactoUsuario(c.getUltimoContactoUsuario())
                .ultimoContactoResultado(c.getUltimoContactoResultado())
                .tratamientoId(c.getTratamientoId())
                .doctorDerivadorId(c.getDoctorDerivadorId())
                .pacienteDirecto(c.isPacienteDirecto())
                .build();
    }
}
