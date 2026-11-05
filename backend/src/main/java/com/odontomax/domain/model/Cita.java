package com.odontomax.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Cita {

    private UUID id;
    private UUID pacienteId;
    private String pacienteNombre;
    private String pacienteTelefono;
    private UUID doctorId;
    private String doctorNombre;
    private String sedeId;
    private Integer sillonNumero;

    private String motivoConsulta; // Tratamiento solicitado
    private LocalDate fecha;
    private LocalTime hora;
    private int duracionMinutos;

    // Estados clínicos: PENDIENTE_WEB, PROGRAMADA, EN_SALA, EN_ATENCION, ATENDIDA, FALTO, CANCELADA
    private String estado;
    private String motivoCancelacion;
    private String motivoReprogramacion;
    private String notas;
    private String codigoTicket; // Código para el paciente
}
