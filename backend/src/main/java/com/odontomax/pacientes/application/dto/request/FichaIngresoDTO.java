package com.odontomax.pacientes.application.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Historia clínica general del paciente (ficha de ingreso), ordenada por secciones. Todo es opcional: se completa al
 * ingresar y se puede ampliar después. Se guarda como un documento; los campos desconocidos se ignoran.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class FichaIngresoDTO {

    // ───── Datos personales ─────
    @Size(max = 20) private String tipoDocumento;
    @Size(max = 120) private String lugarNacimiento;
    @Size(max = 20) private String estadoCivil;
    @Size(max = 60) private String gradoInstruccion;
    @Size(max = 120) private String ocupacion;
    @Size(max = 120) private String centroTrabajo;
    @Size(max = 120) private String centroEstudios;
    @Min(0) @Max(30) private Integer hijos;
    @Size(max = 200) private String direccion;
    @Size(max = 120) private String urbanizacion;
    @Size(max = 60) private String pais;
    @Size(max = 60) private String departamento;
    @Size(max = 60) private String provincia;
    @Size(max = 60) private String distrito;
    @Size(max = 120) private String correoAlternativo;
    @Size(max = 20) private String telefonoFijo;

    // ───── En caso de emergencia ─────
    @Size(max = 120) private String emergenciaNombre;
    @Size(max = 20) private String emergenciaTelefono;
    @Size(max = 40) private String emergenciaParentesco;

    // ───── Apoderado (paciente menor de edad) ─────
    @Size(max = 100) private String apoderadoNombre;
    @Size(max = 100) private String apoderadoApellido;
    @Size(max = 20) private String apoderadoDni;
    @Size(max = 40) private String apoderadoResponsabilidad;

    // ───── Seguro (todavía opcional) ─────
    @Size(max = 120) private String seguroCompania;
    @Size(max = 60) private String seguroPoliza;

    // ───── Motivo y datos de atención ─────
    @Size(max = 500) private String motivoConsulta;
    @Size(max = 1000) private String observaciones;
    @Size(max = 60) private String ultimaVisitaDentista;
    @Size(max = 200) private String motivoUltimaVisita;
    @Size(max = 40) private String fichaAnterior;
    private Boolean pacienteConRiesgo;

    // ───── Examen clínico y estomatológico ─────
    private Boolean sangradoEncias;
    private Boolean bruxismo;
    private Boolean mucosaBucalAlterada;
    private Boolean dolor;
    private Boolean movilidadDentaria;
    @Size(max = 60) private String frecuenciaCepillado;
    @Size(max = 60) private String frecuenciaVisitaDentista;
    @Size(max = 300) private String experienciasTraumaticas;
    private LocalDate ultimaProfilaxis;
    private LocalDate siguienteProfilaxis;

    // ───── Historial de enfermedades (las alergias están en los antecedentes) ─────
    private Boolean enfVenereas;
    private Boolean hemorragias;
    private Boolean alteracionRespiratoria;
    private Boolean enfermedadRenal;
    private Boolean infartoCardiaco;
    private Boolean anginaPecho;
    private Boolean diabetes;
    private Boolean hepatitis;
    private Boolean tbc;
    private Boolean presionAlta;
    @Size(max = 300) private String medicamentos;
    @Size(max = 120) private String nombreMedico;

    // ───── Pediatría (si el paciente es niño) ─────
    @Size(max = 200) private String pedMadre;
    @Size(max = 200) private String pedPadre;
    @Size(max = 200) private String pedAbuelosPaternos;
    @Size(max = 200) private String pedAbuelosMaternos;
    @Size(max = 20) private String pedTipoParto;
    @Size(max = 40) private String pedHorasTrabajoParto;
    @Size(max = 40) private String pedSemanasGestacion;
    private Boolean pedLloroAlNacer;
    @Size(max = 500) private String pedObservaciones;

    // ───── Solo en la respuesta ─────
    private OffsetDateTime actualizadoEn;
    @Size(max = 120) private String actualizadoPorNombre;
}
