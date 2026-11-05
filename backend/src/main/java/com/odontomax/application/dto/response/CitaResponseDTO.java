package com.odontomax.application.dto.response;

import com.odontomax.domain.model.Cita;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CitaResponseDTO {

    private UUID id;
    private UUID pacienteId;
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
    private String motivoReprogramacion;
    private String notas;
    private String codigoTicket;

    public static CitaResponseDTO fromDomain(Cita c) {
        if (c == null) return null;
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
                .motivoCancelacion(c.getMotivoCancelacion())
                .motivoReprogramacion(c.getMotivoReprogramacion())
                .notas(c.getNotas())
                .codigoTicket(c.getCodigoTicket())
                .build();
    }
}
