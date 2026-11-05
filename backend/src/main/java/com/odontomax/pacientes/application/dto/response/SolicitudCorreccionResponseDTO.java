package com.odontomax.pacientes.application.dto.response;

import com.odontomax.pacientes.domain.model.SolicitudCorreccion;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class SolicitudCorreccionResponseDTO {
    private UUID id;
    private UUID pacienteId;
    private String sedeId;
    private String nombresAnterior;
    private String apellidosAnterior;
    private String dniAnterior;
    private LocalDate fechaNacimientoAnterior;
    private String nombresNuevo;
    private String apellidosNuevo;
    private String dniNuevo;
    private LocalDate fechaNacimientoNueva;
    private String motivo;
    private String estado;
    private String solicitadoPorNombre;
    private OffsetDateTime solicitadoEn;
    private String resueltoPorNombre;
    private OffsetDateTime resueltoEn;
    private String comentarioResolucion;

    public static SolicitudCorreccionResponseDTO from(SolicitudCorreccion s) {
        return SolicitudCorreccionResponseDTO.builder()
                .id(s.getId()).pacienteId(s.getPacienteId()).sedeId(s.getSedeId())
                .nombresAnterior(s.getNombresAnterior()).apellidosAnterior(s.getApellidosAnterior())
                .dniAnterior(s.getDniAnterior()).fechaNacimientoAnterior(s.getFechaNacimientoAnterior())
                .nombresNuevo(s.getNombresNuevo()).apellidosNuevo(s.getApellidosNuevo())
                .dniNuevo(s.getDniNuevo()).fechaNacimientoNueva(s.getFechaNacimientoNueva())
                .motivo(s.getMotivo()).estado(s.getEstado())
                .solicitadoPorNombre(s.getSolicitadoPorNombre()).solicitadoEn(s.getSolicitadoEn())
                .resueltoPorNombre(s.getResueltoPorNombre()).resueltoEn(s.getResueltoEn())
                .comentarioResolucion(s.getComentarioResolucion())
                .build();
    }
}
