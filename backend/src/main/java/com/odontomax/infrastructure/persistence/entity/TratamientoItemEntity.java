package com.odontomax.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tratamiento_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TratamientoItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tratamiento_id", nullable = false)
    private TratamientoEntity tratamiento;

    @Column(name = "procedimiento_id")
    private String procedimientoId;

    @Column(name = "nombre_procedimiento", nullable = false)
    private String nombreProcedimiento;

    @Column(name = "pieza_fdi")
    private Integer piezaFdi;

    @Column(name = "precio_base", nullable = false, precision = 10, scale = 2)
    private BigDecimal precioBase;

    @Column(name = "cantidad", nullable = false)
    @Builder.Default
    private Integer cantidad = 1;

    @Column(name = "descuento", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal descuento = BigDecimal.ZERO;

    @Column(name = "descuento_autorizado_por")
    private UUID descuentoAutorizadoPor;

    @Column(name = "precio_final", nullable = false, precision = 10, scale = 2)
    private BigDecimal precioFinal;

    @Column(name = "estado", nullable = false)
    @Builder.Default
    private String estado = "PLANIFICADO";

    @Column(name = "observaciones")
    private String observaciones;

    @Column(name = "gravedad")
    private String gravedad;

    @CreationTimestamp
    @Column(name = "creado_en", updatable = false)
    private Instant creadoEn;

    @UpdateTimestamp
    @Column(name = "actualizado_en")
    private Instant actualizadoEn;
}
