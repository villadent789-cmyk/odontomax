package com.odontomax.agenda.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemTareaBandejaDTO {

    private String id;
    private String tipo; // Incluye PASAR_AL_SILLON para citas en sala sin inicio de atención.
    private String prioridad; // ALTA, MEDIA, NORMAL

    private UUID pacienteId;
    private String pacienteNombre;
    private String pacienteTelefono;
    private String pacienteDni;

    private UUID citaId;
    private LocalTime horaCita;
    private Instant llegadaEn;
    private String doctorNombre;
    private String motivoConsulta;
    private String estadoCita;
    private String estadoConfirmacion;

    private int totalInasistencias;
    private boolean avisoInasistencias;

    private String mensajeSugerido;
    private String enlaceWhatsapp;
    private Long minutosParaExpirar;
}
