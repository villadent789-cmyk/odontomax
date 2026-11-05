package com.odontomax.promociones.domain.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public class ConfiguracionPromocion {

    private UUID id;
    private String sedeId;
    private TipoPromocion tipo;
    private BigDecimal valor;
    private String descripcionBeneficio;
    private String plantillaMensaje;
    private int diasVigencia;
    private boolean activa;
    private OffsetDateTime creadoEn;
    private OffsetDateTime actualizadoEn;

    public ConfiguracionPromocion() {
    }

    public ConfiguracionPromocion(UUID id, String sedeId, TipoPromocion tipo, BigDecimal valor,
                                  String descripcionBeneficio, String plantillaMensaje,
                                  int diasVigencia, boolean activa, OffsetDateTime creadoEn,
                                  OffsetDateTime actualizadoEn) {
        this.id = id;
        this.sedeId = sedeId;
        this.tipo = tipo;
        this.valor = valor;
        this.descripcionBeneficio = descripcionBeneficio;
        this.plantillaMensaje = plantillaMensaje;
        this.diasVigencia = diasVigencia;
        this.activa = activa;
        this.creadoEn = creadoEn;
        this.actualizadoEn = actualizadoEn;
    }

    public void actualizar(TipoPromocion tipo, BigDecimal valor, String descripcionBeneficio,
                           String plantillaMensaje, int diasVigencia, boolean activa) {
        this.tipo = tipo != null ? tipo : this.tipo;
        this.valor = valor != null ? valor : this.valor;
        this.descripcionBeneficio = descripcionBeneficio != null && !descripcionBeneficio.isBlank()
                ? descripcionBeneficio : this.descripcionBeneficio;
        this.plantillaMensaje = plantillaMensaje != null && !plantillaMensaje.isBlank()
                ? plantillaMensaje : this.plantillaMensaje;
        if (diasVigencia > 0) {
            this.diasVigencia = diasVigencia;
        }
        this.activa = activa;
        this.actualizadoEn = OffsetDateTime.now();
    }

    public String generarMensaje(String nombrePaciente, String nombreSede) {
        String plantilla = this.plantillaMensaje != null ? this.plantillaMensaje : "";
        return plantilla
                .replace("{paciente}", nombrePaciente != null ? nombrePaciente : "estimado(a) paciente")
                .replace("{sede}", nombreSede != null ? nombreSede : "la clínica")
                .replace("{beneficio}", this.descripcionBeneficio != null ? this.descripcionBeneficio : "beneficio especial")
                .replace("{dias}", String.valueOf(this.diasVigencia));
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getSedeId() {
        return sedeId;
    }

    public void setSedeId(String sedeId) {
        this.sedeId = sedeId;
    }

    public TipoPromocion getTipo() {
        return tipo;
    }

    public void setTipo(TipoPromocion tipo) {
        this.tipo = tipo;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public void setValor(BigDecimal valor) {
        this.valor = valor;
    }

    public String getDescripcionBeneficio() {
        return descripcionBeneficio;
    }

    public void setDescripcionBeneficio(String descripcionBeneficio) {
        this.descripcionBeneficio = descripcionBeneficio;
    }

    public String getPlantillaMensaje() {
        return plantillaMensaje;
    }

    public void setPlantillaMensaje(String plantillaMensaje) {
        this.plantillaMensaje = plantillaMensaje;
    }

    public int getDiasVigencia() {
        return diasVigencia;
    }

    public void setDiasVigencia(int diasVigencia) {
        this.diasVigencia = diasVigencia;
    }

    public boolean isActiva() {
        return activa;
    }

    public void setActiva(boolean activa) {
        this.activa = activa;
    }

    public OffsetDateTime getCreadoEn() {
        return creadoEn;
    }

    public void setCreadoEn(OffsetDateTime creadoEn) {
        this.creadoEn = creadoEn;
    }

    public OffsetDateTime getActualizadoEn() {
        return actualizadoEn;
    }

    public void setActualizadoEn(OffsetDateTime actualizadoEn) {
        this.actualizadoEn = actualizadoEn;
    }
}
