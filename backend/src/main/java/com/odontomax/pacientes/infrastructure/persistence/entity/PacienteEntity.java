package com.odontomax.pacientes.infrastructure.persistence.entity;

import com.odontomax.pacientes.domain.exception.PacienteConRegistrosProtegidosException;
import com.odontomax.pacientes.domain.exception.PacienteDeudorException;
import com.odontomax.pacientes.domain.model.RegistrosProtegidos;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "pacientes")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PacienteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 20)
    private String dni;

    @Column(nullable = false, length = 100)
    private String nombres;

    @Column(nullable = false, length = 100)
    private String apellidos;

    @Column(name = "fecha_nacimiento")
    private LocalDate fechaNacimiento;

    @Column(length = 25)
    private String telefono;

    @Column(length = 100)
    private String email;

    @Column(name = "sede_id", nullable = false)
    private String sedeId;

    /** La fija la base de datos a partir de la sede (trigger de V40); es de solo lectura. */
    /** Cuándo se subió o cambió la foto de perfil; nulo si el paciente no tiene foto. */
    @Column(name = "foto_actualizada_en")
    private java.time.OffsetDateTime fotoActualizadaEn;

    @Column(name = "unidad_de_negocio", insertable = false, updatable = false)
    private String unidadDeNegocio;

    @Column(name = "genero", length = 20)
    private String genero;

    @Column(name = "fuente_captacion", length = 50)
    private String fuenteCaptacion;

    @Column(name = "referido_por_paciente_id")
    private UUID referidoPorPacienteId;

    @Column(name = "referido_por_paciente_nombre", length = 200)
    private String referidoPorPacienteNombre;

    @Column(name = "doctor_referidor", length = 200)
    private String doctorReferidor;

    @Builder.Default
    @Column(name = "consentimiento_promociones", nullable = false)
    private Boolean consentimientoPromociones = false;

    @Column(name = "consentimiento_promociones_fecha")
    private OffsetDateTime consentimientoPromocionesFecha;

    @Column(name = "numero_historia_clinica", length = 50)
    private String numeroHistoriaClinica;

    @Builder.Default
    @Column(name = "total_inasistencias", nullable = false)
    private Integer totalInasistencias = 0;

    @Column(name = "alergias", columnDefinition = "text")
    private String alergias;

    @Column(name = "antecedentes_medicos", columnDefinition = "text")
    private String antecedentesMedicos;

    @Column(name = "es_gestante")
    private Boolean gestante;

    @Column(name = "semanas_gestacion")
    private Integer semanasGestacion;

    @Column(name = "saldo_pendiente", precision = 10, scale = 2)
    private BigDecimal saldoPendiente;

    @Column(name = "consentimiento_datos_en")
    private OffsetDateTime consentimientoDatosEn;

    @Builder.Default
    @Column(nullable = false)
    private Boolean archivado = false;

    @Column(name = "motivo_archivo", columnDefinition = "text")
    private String motivoArchivo;

    @Builder.Default
    @Column(name = "creado_en", updatable = false)
    private OffsetDateTime creadoEn = OffsetDateTime.now();

    @PrePersist
    public void prePersist() {
        if (this.creadoEn == null) {
            this.creadoEn = OffsetDateTime.now();
        }
        if (this.archivado == null) {
            this.archivado = false;
        }
        if (this.saldoPendiente == null) {
            this.saldoPendiente = BigDecimal.ZERO;
        }
        if (this.consentimientoPromociones == null) {
            this.consentimientoPromociones = false;
        }
        if (this.gestante == null) {
            this.gestante = false;
        }
        if (this.totalInasistencias == null) {
            this.totalInasistencias = 0;
        }
    }

    public String getNombreCompleto() {
        return (nombres + (apellidos != null && !apellidos.isBlank() ? " " + apellidos : "")).trim();
    }

    public int getEdad() {
        return fechaNacimiento != null ? Period.between(fechaNacimiento, LocalDate.now()).getYears() : 0;
    }

    public String numeroHistoriaVisible() {
        if (numeroHistoriaClinica != null && !numeroHistoriaClinica.isBlank()) {
            return numeroHistoriaClinica;
        }
        return (dni != null && !dni.isBlank()) ? "HC-" + dni : "-";
    }

    public boolean tieneAvisoInasistencias() {
        return totalInasistencias != null && totalInasistencias >= 2;
    }

    public boolean esCumpleaneroDelMes(LocalDate hoy) {
        return fechaNacimiento != null && hoy != null && fechaNacimiento.getMonth() == hoy.getMonth();
    }

    public boolean tieneDeuda() {
        return saldoPendiente != null && saldoPendiente.compareTo(BigDecimal.ZERO) > 0;
    }

    public List<String> getAlergiasLista() {
        return alergias != null && !alergias.isBlank() ? Arrays.asList(alergias.split(",")) : Collections.emptyList();
    }

    public List<String> alergiasConEspaciosNormalizados() {
        if (alergias == null || alergias.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(alergias.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    public void verificarArchivable(String motivo) {
        if (tieneDeuda() && (motivo == null || motivo.trim().length() < 5)) {
            throw new PacienteDeudorException("El paciente mantiene una deuda pendiente de S/ "
                    + saldoPendiente + ". Es obligatorio registrar un motivo detallado (ej. Mudanza, Deuda incobrable).");
        }
    }

    public void verificarEliminable(RegistrosProtegidos registros) {
        if (registros.notasEvolucion() > 0 || registros.antecedentes() > 0 || registros.accesos() > 0 || registros.pagos() > 0) {
            throw new PacienteConRegistrosProtegidosException(
                    String.format("No se puede eliminar permanentemente el paciente porque cuenta con registros médicos o financieros protegidos " +
                            "(notas: %d, antecedentes: %d, accesos: %d, pagos: %d). " +
                            "De acuerdo con la normativa legal de salud, el expediente únicamente puede ser ARCHIVADO.",
                            registros.notasEvolucion(), registros.antecedentes(), registros.accesos(), registros.pagos()));
        }
        if (registros.consentimientos() > 0) {
            throw new PacienteConRegistrosProtegidosException(
                    "El paciente tiene consentimientos firmados (registro legal inmutable) y no puede eliminarse; "
                            + "archive la ficha en su lugar.");
        }
        if (registros.ordenesLaboratorio() > 0) {
            throw new PacienteConRegistrosProtegidosException(
                    "El paciente tiene órdenes de laboratorio registradas y no puede eliminarse; "
                            + "archive la ficha en su lugar.");
        }
    }

    public void actualizarAntecedentes(String alergiasTexto, String antecedentesMedicos, Boolean gestante, Integer semanasGestacion) {
        this.alergias = alergiasTexto;
        this.antecedentesMedicos = antecedentesMedicos;
        if (gestante != null) {
            this.gestante = gestante;
            this.semanasGestacion = Boolean.TRUE.equals(gestante) ? semanasGestacion : null;
        }
    }

    public void registrarConsentimientoDatos(OffsetDateTime instante) {
        this.consentimientoDatosEn = instante;
    }

    public static String motivoParaAuditoria(String motivo) {
        if (motivo == null || motivo.isBlank()) {
            return "no indicado";
        }
        String limpio = motivo.trim().replaceAll("\\s+", " ");
        return limpio.length() > 500 ? limpio.substring(0, 500) : limpio;
    }
}
