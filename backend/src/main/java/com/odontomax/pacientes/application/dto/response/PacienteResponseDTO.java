package com.odontomax.pacientes.application.dto.response;

import com.odontomax.pacientes.domain.model.Paciente;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PacienteResponseDTO {

    private UUID id;
    private String dni;
    private String nombres;
    private String apellidos;
    private String nombreCompleto;
    private LocalDate fechaNacimiento;
    private int edad;
    private String telefono;
    private String email;
    private String sedeId;

    private String genero;
    private String fuenteCaptacion;
    private UUID referidoPorPacienteId;
    private String referidoPorPacienteNombre;
    private String doctorReferidor;
    private boolean consentimientoPromociones;
    private java.time.OffsetDateTime consentimientoPromocionesFecha;
    private String numeroHistoriaClinica;
    private int totalInasistencias;
    /** Regla Sección 5: A partir de 2 inasistencias el paciente queda marcado */
    private boolean avisoInasistencias;
    /** Regla Sección 6: Cumpleaños en el mes actual */
    private boolean esCumpleaneroMes;

    private List<String> alergias;
    private String antecedentesMedicos;
    private boolean gestante;
    private Integer semanasGestacion;

    private BigDecimal saldoPendiente;
    private boolean archivado;
    private boolean tieneDeuda;

    /** Fecha de la última cita ATENDIDA (null si nunca fue atendido). La completa el controlador. */
    private LocalDate ultimaCitaAtendida;
    /** Cuándo se cambió la foto de perfil; nulo si el paciente no tiene foto. */
    private java.time.OffsetDateTime fotoActualizadaEn;

    public static PacienteResponseDTO fromEntity(com.odontomax.pacientes.infrastructure.persistence.entity.PacienteEntity p) {
        if (p == null) return null;
        return PacienteResponseDTO.builder()
                .id(p.getId())
                .dni(p.getDni())
                .nombres(p.getNombres())
                .apellidos(p.getApellidos())
                .nombreCompleto(p.getNombreCompleto())
                .fechaNacimiento(p.getFechaNacimiento())
                .edad(p.getEdad())
                .telefono(p.getTelefono())
                .email(p.getEmail())
                .sedeId(p.getSedeId())
                .genero(p.getGenero())
                .fuenteCaptacion(p.getFuenteCaptacion())
                .referidoPorPacienteId(p.getReferidoPorPacienteId())
                .referidoPorPacienteNombre(p.getReferidoPorPacienteNombre())
                .doctorReferidor(p.getDoctorReferidor())
                .consentimientoPromociones(Boolean.TRUE.equals(p.getConsentimientoPromociones()))
                .consentimientoPromocionesFecha(p.getConsentimientoPromocionesFecha())
                .numeroHistoriaClinica(p.numeroHistoriaVisible())
                .totalInasistencias(p.getTotalInasistencias() != null ? p.getTotalInasistencias() : 0)
                .avisoInasistencias(p.tieneAvisoInasistencias())
                .esCumpleaneroMes(p.esCumpleaneroDelMes(LocalDate.now()))
                .alergias(p.getAlergiasLista())
                .antecedentesMedicos(p.getAntecedentesMedicos())
                .gestante(Boolean.TRUE.equals(p.getGestante()))
                .semanasGestacion(p.getSemanasGestacion())
                .saldoPendiente(p.getSaldoPendiente())
                .archivado(Boolean.TRUE.equals(p.getArchivado()))
                .tieneDeuda(p.tieneDeuda())
                .fotoActualizadaEn(p.getFotoActualizadaEn())
                .build();
    }

    public static PacienteResponseDTO fromDomain(Paciente p) {
        if (p == null) return null;
        return PacienteResponseDTO.builder()
                .id(p.getId())
                .dni(p.getDni())
                .nombres(p.getNombres())
                .apellidos(p.getApellidos())
                .nombreCompleto(p.getNombreCompleto())
                .fechaNacimiento(p.getFechaNacimiento())
                .edad(p.getEdad())
                .telefono(p.getTelefono())
                .email(p.getEmail())
                .sedeId(p.getSedeId())
                .genero(p.getGenero())
                .fuenteCaptacion(p.getFuenteCaptacion())
                .referidoPorPacienteId(p.getReferidoPorPacienteId())
                .referidoPorPacienteNombre(p.getReferidoPorPacienteNombre())
                .doctorReferidor(p.getDoctorReferidor())
                .consentimientoPromociones(p.isConsentimientoPromociones())
                .consentimientoPromocionesFecha(p.getConsentimientoPromocionesFecha())
                .numeroHistoriaClinica(p.numeroHistoriaVisible())
                .totalInasistencias(p.getTotalInasistencias())
                .avisoInasistencias(p.tieneAvisoInasistencias())
                .esCumpleaneroMes(p.esCumpleaneroDelMes(LocalDate.now()))
                .alergias(p.getAlergias())
                .antecedentesMedicos(p.getAntecedentesMedicos())
                .gestante(p.isGestante())
                .semanasGestacion(p.getSemanasGestacion())
                .saldoPendiente(p.getSaldoPendiente())
                .archivado(p.isArchivado())
                .tieneDeuda(p.tieneDeuda())
                .build();
    }
}
