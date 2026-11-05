package com.odontomax.sedes.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "sedes")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SedeEntity {

    /** Id legible ("sede-chilca-1"); lo genera SedeService a partir del nombre. */
    @Id
    private String id;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private String direccion;

    @Column(nullable = false)
    private String ciudad;

    private String telefono;

    private String whatsapp;

    private String email;

    @Column(name = "horario_texto")
    private String horarioTexto;

    private String color;

    @Column(name = "color_secundario")
    private String colorSecundario;

    @Column(nullable = false)
    private Integer sillones;

    @Column(columnDefinition = "text")
    private String descripcion;

    @Column(name = "foto_portada")
    private String fotoPortada;

    @Builder.Default
    @Column(name = "fotos", columnDefinition = "text[]", nullable = false)
    private String[] fotos = new String[0];

    @Column(precision = 9, scale = 6)
    private BigDecimal latitud;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitud;

    @Builder.Default
    @Column(nullable = false)
    private boolean activa = true;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "unidad_de_negocio", nullable = false)
    private com.odontomax.sedes.model.UnidadDeNegocio unidadDeNegocio = com.odontomax.sedes.model.UnidadDeNegocio.VILLADENT;

    @Column(name = "ruc")
    private String ruc;

    @Builder.Default
    @Column(name = "monto_adelanto_defecto", nullable = false)
    private BigDecimal montoAdelantoDefecto = new BigDecimal("30.00");

    @Builder.Default
    @Column(name = "horas_expiracion_adelanto", nullable = false)
    private Integer horasExpiracionAdelanto = 2;

    @Builder.Default
    @Column(name = "horas_anticipacion_cancelacion", nullable = false)
    private Integer horasAnticipacionCancelacion = 24;

    @Builder.Default
    @Column(name = "horas_recordatorio_whatsapp", nullable = false)
    private Integer horasRecordatorioWhatsapp = 24;

    @Builder.Default
    @Column(name = "hora_inicio_llamadas", nullable = false)
    private java.time.LocalTime horaInicioLlamadas = java.time.LocalTime.of(8, 30);

    @Builder.Default
    @Column(name = "dias_vigencia_promocion_cumpleanos", nullable = false)
    private Integer diasVigenciaPromocionCumpleanos = 7;

    @Column(name = "creado_en", insertable = false, updatable = false)
    private OffsetDateTime creadoEn;

    @Column(name = "actualizado_en", insertable = false, updatable = false)
    private OffsetDateTime actualizadoEn;
}
