package com.odontomax.sedes.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "servicios_web")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServicioEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private String especialidad;

    @Column(columnDefinition = "text")
    private String descripcion;

    @Column(name = "duracion_min", nullable = false)
    private Integer duracionMin;

    @Column(name = "precio_desde")
    private BigDecimal precioDesde;

    @Builder.Default
    @Column(nullable = false)
    private boolean activo = true;

    @Builder.Default
    @Column(nullable = false)
    private Integer orden = 0;

    /** VILLADENT (tratamientos de la clínica) o VILLAMAX (estudios radiológicos). */
    @Builder.Default
    @Column(name = "unidad_de_negocio", nullable = false)
    private String unidadDeNegocio = "VILLADENT";

    /** Si el servicio se ofrece en la reserva web; el tarifario detallado se usa solo en planes y cobros. */
    @Builder.Default
    @Column(name = "visible_en_reserva", nullable = false)
    private boolean visibleEnReserva = true;

    /** Porcentaje del precio que se lleva el doctor externo que deriva; solo aplica a los estudios de Villamax. */
    @Column(name = "comision_porcentaje")
    private BigDecimal comisionPorcentaje;

    /** Monto fijo (S/) por estudio para el doctor externo; manda sobre el porcentaje y se multiplica por la cantidad. */
    @Column(name = "comision_fija")
    private BigDecimal comisionFija;

    /** Precio por pieza según la gravedad del caso; si falta alguno, se usa el precio general. */
    @Column(name = "precio_leve")
    private BigDecimal precioLeve;

    @Column(name = "precio_moderado")
    private BigDecimal precioModerado;

    @Column(name = "precio_severo")
    private BigDecimal precioSevero;
}
