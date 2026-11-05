package com.odontomax.pacientes.domain.model;

import com.odontomax.pacientes.domain.exception.PacienteConRegistrosProtegidosException;
import com.odontomax.pacientes.domain.exception.PacienteDeudorException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Paciente {

    /** Desde esta cantidad de inasistencias el paciente queda marcado. */
    public static final int INASISTENCIAS_PARA_AVISO = 2;

    /** Caracteres minimos (tras trim) del motivo para archivar a un paciente con deuda. */
    private static final int MOTIVO_MINIMO_CON_DEUDA = 5;

    /** Datos editables de un paciente (alta y edicion); el dominio no valida formatos, eso es de la frontera web. */
    public record DatosPaciente(
            String dni,
            String nombres,
            String apellidos,
            LocalDate fechaNacimiento,
            String telefono,
            String email,
            String genero,
            String fuenteCaptacion,
            UUID referidoPorPacienteId,
            String referidoPorPacienteNombre,
            String doctorReferidor,
            boolean consentimientoPromociones,
            String numeroHistoriaClinica,
            List<String> alergias,
            String antecedentesMedicos,
            boolean gestante,
            Integer semanasGestacion
    ) {
    }

    private UUID id;
    private String dni;
    private String nombres;
    private String apellidos;
    private LocalDate fechaNacimiento;
    private String telefono;
    private String email;
    private String sedeId;

    // Datos demograficos y marketing v2
    private String genero;
    private String fuenteCaptacion;
    private UUID referidoPorPacienteId;
    private String referidoPorPacienteNombre;
    private String doctorReferidor;
    private boolean consentimientoPromociones;
    private OffsetDateTime consentimientoPromocionesFecha;
    private String numeroHistoriaClinica;
    private int totalInasistencias;

    // Alertas clinicas criticas
    private String alergiasTexto;
    private List<String> alergias;
    private String antecedentesMedicos;
    private boolean gestante;
    private Integer semanasGestacion;

    // Estado financiero
    private BigDecimal saldoPendiente;

    // Trazabilidad y borrado logico
    private boolean archivado;
    private String motivoArchivo;

    /** Fecha de alta; solo lectura (la fija la persistencia). */
    private OffsetDateTime creadoEn;

    /** Consentimiento de tratamiento de datos personales (Ley 29733). */
    private OffsetDateTime consentimientoDatosEn;

    /** Alta: datos recortados, sin saldo, no archivado; la fecha de consentimiento de promociones solo si lo acepta. */
    public static Paciente registrar(DatosPaciente datos, String sedeEfectiva, OffsetDateTime ahora) {
        String textoAlergias = (datos.alergias() != null && !datos.alergias().isEmpty())
                ? String.join(",", datos.alergias())
                : null;
        return Paciente.builder()
                .id(UUID.randomUUID())
                .dni(datos.dni().trim())
                .nombres(datos.nombres().trim())
                .apellidos(datos.apellidos().trim())
                .fechaNacimiento(datos.fechaNacimiento())
                .telefono(datos.telefono())
                .email(datos.email())
                .sedeId(sedeEfectiva)
                .genero(datos.genero())
                .fuenteCaptacion(datos.fuenteCaptacion())
                .referidoPorPacienteId(datos.referidoPorPacienteId())
                .referidoPorPacienteNombre(datos.referidoPorPacienteNombre())
                .doctorReferidor(datos.doctorReferidor())
                .consentimientoPromociones(datos.consentimientoPromociones())
                .consentimientoPromocionesFecha(datos.consentimientoPromociones() ? ahora : null)
                .numeroHistoriaClinica(datos.numeroHistoriaClinica())
                .alergiasTexto(textoAlergias)
                .alergias(datos.alergias())
                .antecedentesMedicos(datos.antecedentesMedicos())
                .gestante(datos.gestante())
                .semanasGestacion(datos.semanasGestacion())
                .saldoPendiente(BigDecimal.ZERO)
                .archivado(false)
                .build();
    }

    /**
     * Edicion: reemplaza los datos editables. La fecha de consentimiento de promociones solo se fija en el paso de falso
     * a verdadero y nunca se borra; el numero de historia solo cambia si viene informado. No toca saldo, archivado,
     * motivo de archivo ni inasistencias.
     */
    public void actualizarDatos(DatosPaciente datos, String sedeEfectiva, OffsetDateTime ahora) {
        this.dni = datos.dni().trim();
        this.nombres = datos.nombres().trim();
        this.apellidos = datos.apellidos().trim();
        this.fechaNacimiento = datos.fechaNacimiento();
        this.telefono = datos.telefono();
        this.email = datos.email();
        this.sedeId = sedeEfectiva;
        this.genero = datos.genero();
        this.fuenteCaptacion = datos.fuenteCaptacion();
        this.referidoPorPacienteId = datos.referidoPorPacienteId();
        this.referidoPorPacienteNombre = datos.referidoPorPacienteNombre();
        this.doctorReferidor = datos.doctorReferidor();
        if (datos.consentimientoPromociones() && !this.consentimientoPromociones) {
            this.consentimientoPromocionesFecha = ahora;
        }
        this.consentimientoPromociones = datos.consentimientoPromociones();
        if (datos.numeroHistoriaClinica() != null && !datos.numeroHistoriaClinica().isBlank()) {
            this.numeroHistoriaClinica = datos.numeroHistoriaClinica();
        }
        this.alergiasTexto = (datos.alergias() != null && !datos.alergias().isEmpty())
                ? String.join(",", datos.alergias())
                : null;
        this.alergias = datos.alergias();
        this.antecedentesMedicos = datos.antecedentesMedicos();
        this.gestante = datos.gestante();
        this.semanasGestacion = datos.semanasGestacion();
    }

    /** Con deuda pendiente es obligatorio un motivo de al menos 5 caracteres (tras trim) para archivar. */
    public void verificarArchivable(String motivo) {
        if (tieneDeuda() && (motivo == null || motivo.trim().length() < MOTIVO_MINIMO_CON_DEUDA)) {
            throw new PacienteDeudorException("El paciente mantiene una deuda pendiente de S/ "
                    + saldoPendiente + ". Es obligatorio registrar un motivo detallado (ej. Mudanza, Deuda incobrable).");
        }
    }

    /**
     * Registros que protegen la eliminacion fisica (el expediente solo puede archivarse): notas de evolucion,
     * antecedentes, accesos a la historia y pagos (con contadores), consentimientos firmados (inmutables) y ordenes de
     * laboratorio. Las citas no protegen.
     */
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

    /**
     * Actualizacion de antecedentes y alergias (regla seccion 5.2). Gestante solo cambia si viene informado;
     * semanas de gestacion se anula si gestante es falso.
     */
    public void actualizarAntecedentes(String alergiasTexto, String antecedentesMedicos, Boolean gestante, Integer semanasGestacion) {
        this.alergiasTexto = alergiasTexto;
        this.alergias = (alergiasTexto != null && !alergiasTexto.isBlank())
                ? Arrays.asList(alergiasTexto.split(","))
                : Collections.emptyList();
        this.antecedentesMedicos = antecedentesMedicos;
        if (gestante != null) {
            this.gestante = gestante;
            this.semanasGestacion = Boolean.TRUE.equals(gestante) ? semanasGestacion : null;
        }
    }

    /** Registra la fecha del consentimiento de tratamiento de datos personales (Ley 29733). */
    public void registrarConsentimientoDatos(OffsetDateTime instante) {
        this.consentimientoDatosEn = instante;
    }

    /** Alergias separadas recortando espacios en blanco alrededor de las comas (B-049, PUT /antecedentes y exportar). */
    public List<String> alergiasConEspaciosNormalizados() {
        return (alergiasTexto != null && !alergiasTexto.isBlank())
                ? Arrays.asList(alergiasTexto.split("\\s*,\\s*"))
                : Collections.emptyList();
    }

    /** Motivo de eliminacion para la auditoria: recortado, espacios internos colapsados, maximo 500; "no indicado" si no hay. */
    public static String motivoParaAuditoria(String motivo) {
        if (motivo == null || motivo.isBlank()) {
            return "no indicado";
        }
        String limpio = motivo.trim().replaceAll("\\s+", " ");
        return limpio.length() > 500 ? limpio.substring(0, 500) : limpio;
    }

    public String getNombreCompleto() {
        return (nombres != null ? nombres : "") + " " + (apellidos != null ? apellidos : "");
    }

    public int getEdad() {
        if (fechaNacimiento == null) return 0;
        return Period.between(fechaNacimiento, LocalDate.now()).getYears();
    }

    public boolean tieneDeuda() {
        return saldoPendiente != null && saldoPendiente.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean tieneAvisoInasistencias() {
        return totalInasistencias >= INASISTENCIAS_PARA_AVISO;
    }

    public boolean esCumpleaneroDelMes(LocalDate hoy) {
        return fechaNacimiento != null && fechaNacimiento.getMonth() == hoy.getMonth();
    }

    /** El numero de historia informado, o "HC-" + dni cuando no hay (null si tampoco hay dni). */
    public String numeroHistoriaVisible() {
        if (numeroHistoriaClinica != null && !numeroHistoriaClinica.isBlank()) {
            return numeroHistoriaClinica;
        }
        return dni != null ? "HC-" + dni : null;
    }
}