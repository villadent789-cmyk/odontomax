package com.odontomax.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ordenes_laboratorio")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrdenLaboratorioEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String codigo;

    @Column(name = "paciente_id", nullable = false)
    private UUID pacienteId;

    @Column(name = "especialista_id", nullable = false)
    private UUID especialistaId;

    @Column(name = "sede_id", nullable = false)
    private String sedeId;

    @Column(name = "laboratorio_id", nullable = false)
    private UUID laboratorioId;

    @Column(name = "tipo_trabajo", nullable = false)
    private String tipoTrabajo;

    @Column(name = "color_tono")
    private String colorTono;

    @Column(length = 100)
    private String material;

    @Builder.Default
    @Column(nullable = false)
    private boolean provisional = false;

    @Column(name = "piezas_dentales")
    private String piezasDentales;

    @Builder.Default
    @Column(nullable = false)
    private String estado = "ENVIADO";

    @Builder.Default
    @Column(name = "fecha_solicitud", nullable = false)
    private LocalDate fechaSolicitud = LocalDate.now();

    @Column(name = "fecha_envio_laboratorio")
    private OffsetDateTime fechaEnvioLaboratorio;

    @Column(name = "fecha_estimada_entrega")
    private LocalDate fechaEstimadaEntrega;

    @Column(name = "fecha_recepcion_clinica")
    private OffsetDateTime fechaRecepcionClinica;

    @Column(name = "fecha_instalacion")
    private OffsetDateTime fechaInstalacion;

    @Column(name = "costo_laboratorio")
    private BigDecimal costoLaboratorio;

    @Column(name = "precio_paciente")
    private BigDecimal precioPaciente;

    @Column(name = "guia_remision")
    private String guiaRemision;

    @Column(name = "indicaciones_tecnicas", columnDefinition = "TEXT")
    private String indicacionesTecnicas;

    @Column(columnDefinition = "TEXT")
    private String observaciones;

    @Column(name = "registrado_por")
    private String registradoPor;

    @Builder.Default
    @Column(name = "creado_en", updatable = false)
    private OffsetDateTime creadoEn = OffsetDateTime.now();

    @Builder.Default
    @Column(name = "actualizado_en")
    private OffsetDateTime actualizadoEn = OffsetDateTime.now();

    @PrePersist
    public void prePersist() {
        if (this.fechaSolicitud == null) {
            this.fechaSolicitud = LocalDate.now();
        }
        if (this.creadoEn == null) {
            this.creadoEn = OffsetDateTime.now();
        }
        if (this.actualizadoEn == null) {
            this.actualizadoEn = OffsetDateTime.now();
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.actualizadoEn = OffsetDateTime.now();
    }
}
