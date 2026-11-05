package com.odontomax.promociones.domain.model;

import com.odontomax.promociones.domain.exception.PromocionVencidaException;
import com.odontomax.promociones.domain.exception.PromocionYaUsadaException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public class PromocionCumpleanos {

    private UUID id;
    private UUID pacienteId;
    private String sedeId;
    private int anio;
    private TipoPromocion tipo;
    private BigDecimal valor;
    private String beneficio;
    private LocalDate fechaEmision;
    private LocalDate fechaVencimiento;
    private String mensajePersonalizado;
    private boolean enviadoWhatsapp;
    private OffsetDateTime enviadoEn;
    private String enviadoPor;
    private boolean usado;
    private OffsetDateTime usadoEn;
    private UUID citaId;
    private String usadoPor;
    private String notasUso;
    private OffsetDateTime creadoEn;

    public PromocionCumpleanos() {
    }

    public PromocionCumpleanos(UUID id, UUID pacienteId, String sedeId, int anio, TipoPromocion tipo,
                              BigDecimal valor, String beneficio, LocalDate fechaEmision,
                              LocalDate fechaVencimiento, String mensajePersonalizado,
                              boolean enviadoWhatsapp, OffsetDateTime enviadoEn, String enviadoPor,
                              boolean usado, OffsetDateTime usadoEn, UUID citaId, String usadoPor,
                              String notasUso, OffsetDateTime creadoEn) {
        this.id = id;
        this.pacienteId = pacienteId;
        this.sedeId = sedeId;
        this.anio = anio;
        this.tipo = tipo;
        this.valor = valor;
        this.beneficio = beneficio;
        this.fechaEmision = fechaEmision;
        this.fechaVencimiento = fechaVencimiento;
        this.mensajePersonalizado = mensajePersonalizado;
        this.enviadoWhatsapp = enviadoWhatsapp;
        this.enviadoEn = enviadoEn;
        this.enviadoPor = enviadoPor;
        this.usado = usado;
        this.usadoEn = usadoEn;
        this.citaId = citaId;
        this.usadoPor = usadoPor;
        this.notasUso = notasUso;
        this.creadoEn = creadoEn;
    }

    public static PromocionCumpleanos crear(UUID pacienteId, String sedeId, int anio,
                                           ConfiguracionPromocion config, LocalDate fechaCumpleanos,
                                           String mensajePersonalizado) {
        PromocionCumpleanos p = new PromocionCumpleanos();
        p.setId(UUID.randomUUID());
        p.setPacienteId(pacienteId);
        p.setSedeId(sedeId);
        p.setAnio(anio);
        p.setTipo(config != null && config.getTipo() != null ? config.getTipo() : TipoPromocion.PORCENTAJE);
        p.setValor(config != null ? config.getValor() : new BigDecimal("20.00"));
        p.setBeneficio(config != null && config.getDescripcionBeneficio() != null
                ? config.getDescripcionBeneficio() : "20% de descuento en Profilaxis Dental");
        p.setFechaEmision(fechaCumpleanos);
        int dias = config != null && config.getDiasVigencia() > 0 ? config.getDiasVigencia() : 7;
        p.setFechaVencimiento(fechaCumpleanos.plusDays(dias));
        p.setMensajePersonalizado(mensajePersonalizado);
        p.setEnviadoWhatsapp(false);
        p.setUsado(false);
        p.setCreadoEn(OffsetDateTime.now());
        return p;
    }

    public boolean haVencido(LocalDate hoy) {
        if (this.fechaVencimiento == null) {
            return false;
        }
        return hoy.isAfter(this.fechaVencimiento);
    }

    public boolean esVigente(LocalDate hoy) {
        return !this.usado && !haVencido(hoy);
    }

    public void aplicar(UUID citaId, String usuario, String notas) {
        if (this.usado) {
            throw new PromocionYaUsadaException("La promoción de cumpleaños ya fue utilizada previamente.");
        }
        if (haVencido(LocalDate.now())) {
            throw new PromocionVencidaException("La promoción de cumpleaños ha vencido y no puede ser aplicada.");
        }
        this.usado = true;
        this.usadoEn = OffsetDateTime.now();
        this.citaId = citaId;
        this.usadoPor = usuario != null ? usuario : "Sistema";
        this.notasUso = notas;
    }

    public void marcarEnviada(String usuario) {
        this.enviadoWhatsapp = true;
        this.enviadoEn = OffsetDateTime.now();
        this.enviadoPor = usuario != null ? usuario : "Sistema";
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getPacienteId() {
        return pacienteId;
    }

    public void setPacienteId(UUID pacienteId) {
        this.pacienteId = pacienteId;
    }

    public String getSedeId() {
        return sedeId;
    }

    public void setSedeId(String sedeId) {
        this.sedeId = sedeId;
    }

    public int getAnio() {
        return anio;
    }

    public void setAnio(int anio) {
        this.anio = anio;
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

    public String getBeneficio() {
        return beneficio;
    }

    public void setBeneficio(String beneficio) {
        this.beneficio = beneficio;
    }

    public LocalDate getFechaEmision() {
        return fechaEmision;
    }

    public void setFechaEmision(LocalDate fechaEmision) {
        this.fechaEmision = fechaEmision;
    }

    public LocalDate getFechaVencimiento() {
        return fechaVencimiento;
    }

    public void setFechaVencimiento(LocalDate fechaVencimiento) {
        this.fechaVencimiento = fechaVencimiento;
    }

    public String getMensajePersonalizado() {
        return mensajePersonalizado;
    }

    public void setMensajePersonalizado(String mensajePersonalizado) {
        this.mensajePersonalizado = mensajePersonalizado;
    }

    public boolean isEnviadoWhatsapp() {
        return enviadoWhatsapp;
    }

    public void setEnviadoWhatsapp(boolean enviadoWhatsapp) {
        this.enviadoWhatsapp = enviadoWhatsapp;
    }

    public OffsetDateTime getEnviadoEn() {
        return enviadoEn;
    }

    public void setEnviadoEn(OffsetDateTime enviadoEn) {
        this.enviadoEn = enviadoEn;
    }

    public String getEnviadoPor() {
        return enviadoPor;
    }

    public void setEnviadoPor(String enviadoPor) {
        this.enviadoPor = enviadoPor;
    }

    public boolean isUsado() {
        return usado;
    }

    public void setUsado(boolean usado) {
        this.usado = usado;
    }

    public OffsetDateTime getUsadoEn() {
        return usadoEn;
    }

    public void setUsadoEn(OffsetDateTime usadoEn) {
        this.usadoEn = usadoEn;
    }

    public UUID getCitaId() {
        return citaId;
    }

    public void setCitaId(UUID citaId) {
        this.citaId = citaId;
    }

    public String getUsadoPor() {
        return usadoPor;
    }

    public void setUsadoPor(String usadoPor) {
        this.usadoPor = usadoPor;
    }

    public String getNotasUso() {
        return notasUso;
    }

    public void setNotasUso(String notasUso) {
        this.notasUso = notasUso;
    }

    public OffsetDateTime getCreadoEn() {
        return creadoEn;
    }

    public void setCreadoEn(OffsetDateTime creadoEn) {
        this.creadoEn = creadoEn;
    }
}
