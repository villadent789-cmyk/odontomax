package com.odontomax.laboratorio.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrdenLaboratorioResponseDTO {

    private UUID id;
    private String codigo;

    @JsonProperty("paciente_id")
    private UUID pacienteId;

    @JsonProperty("paciente_nombre")
    private String pacienteNombre;

    @JsonProperty("paciente_dni")
    private String pacienteDni;

    @JsonProperty("especialista_id")
    private UUID especialistaId;

    @JsonProperty("especialista_nombre")
    private String especialistaNombre;

    @JsonProperty("sede_id")
    private String sedeId;

    @JsonProperty("laboratorio_id")
    private UUID laboratorioId;

    @JsonProperty("laboratorio_nombre")
    private String laboratorioNombre;

    @JsonProperty("tipo_trabajo")
    private String tipoTrabajo;

    @JsonProperty("color_tono")
    private String colorTono;

    private String color;

    private String material;

    private boolean provisional;

    @JsonProperty("piezas_dentales")
    private String piezasDentales;

    private String estado;

    @JsonProperty("fecha_solicitud")
    private LocalDate fechaSolicitud;

    @JsonProperty("fecha_envio_laboratorio")
    private OffsetDateTime fechaEnvioLaboratorio;

    @JsonProperty("fecha_estimada_entrega")
    private LocalDate fechaEstimadaEntrega;

    @JsonProperty("fecha_recepcion_clinica")
    private OffsetDateTime fechaRecepcionClinica;

    @JsonProperty("fecha_instalacion")
    private OffsetDateTime fechaInstalacion;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("costo_laboratorio")
    private BigDecimal costoLaboratorio;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("precio_paciente")
    private BigDecimal precioPaciente;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String estadoPago;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private BigDecimal montoPagado;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private BigDecimal saldoLaboratorio;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private BigDecimal ganancia;

    @JsonProperty("guia_remision")
    private String guiaRemision;

    @JsonProperty("indicaciones_tecnicas")
    private String indicacionesTecnicas;

    private String observaciones;

    @JsonProperty("registrado_por")
    private String registradoPor;

    @JsonProperty("creado_en")
    private OffsetDateTime creadoEn;

    @JsonProperty("actualizado_en")
    private OffsetDateTime actualizadoEn;

    private List<OrdenLaboratorioHistorialResponseDTO> historial;
    /** Pruebas con el paciente, de la 1 en adelante. */
    private List<OrdenLaboratorioPruebaResponseDTO> pruebas;

    // CamelCase accessors for seamless frontend usage
    @JsonProperty("pacienteId")
    public UUID getPacienteIdCamel() { return pacienteId; }

    @JsonProperty("pacienteNombre")
    public String getPacienteNombreCamel() { return pacienteNombre; }

    @JsonProperty("pacienteDni")
    public String getPacienteDniCamel() { return pacienteDni; }

    @JsonProperty("especialistaId")
    public UUID getEspecialistaIdCamel() { return especialistaId; }

    @JsonProperty("especialistaNombre")
    public String getEspecialistaNombreCamel() { return especialistaNombre; }

    @JsonProperty("sedeId")
    public String getSedeIdCamel() { return sedeId; }

    @JsonProperty("laboratorioId")
    public UUID getLaboratorioIdCamel() { return laboratorioId; }

    @JsonProperty("laboratorioNombre")
    public String getLaboratorioNombreCamel() { return laboratorioNombre; }

    @JsonProperty("tipoTrabajo")
    public String getTipoTrabajoCamel() { return tipoTrabajo; }

    @JsonProperty("colorTono")
    public String getColorTonoCamel() { return colorTono; }

    @JsonProperty("piezasDentales")
    public String getPiezasDentalesCamel() { return piezasDentales; }

    @JsonProperty("fechaSolicitud")
    public LocalDate getFechaSolicitudCamel() { return fechaSolicitud; }

    @JsonProperty("fechaEnvioLaboratorio")
    public OffsetDateTime getFechaEnvioLaboratorioCamel() { return fechaEnvioLaboratorio; }

    @JsonProperty("fechaEstimadaEntrega")
    public LocalDate getFechaEstimadaEntregaCamel() { return fechaEstimadaEntrega; }

    @JsonProperty("fechaRecepcionClinica")
    public OffsetDateTime getFechaRecepcionClinicaCamel() { return fechaRecepcionClinica; }

    @JsonProperty("fechaInstalacion")
    public OffsetDateTime getFechaInstalacionCamel() { return fechaInstalacion; }

    @JsonProperty("costoLaboratorio")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public BigDecimal getCostoLaboratorioCamel() { return costoLaboratorio; }

    @JsonProperty("precioPaciente")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public BigDecimal getPrecioPacienteCamel() { return precioPaciente; }

    @JsonProperty("guiaRemision")
    public String getGuiaRemisionCamel() { return guiaRemision; }

    @JsonProperty("indicacionesTecnicas")
    public String getIndicacionesTecnicasCamel() { return indicacionesTecnicas; }

    @JsonProperty("registradoPor")
    public String getRegistradoPorCamel() { return registradoPor; }

    @JsonProperty("creadoEn")
    public OffsetDateTime getCreadoEnCamel() { return creadoEn; }

    @JsonProperty("actualizadoEn")
    public OffsetDateTime getActualizadoEnCamel() { return actualizadoEn; }
}
