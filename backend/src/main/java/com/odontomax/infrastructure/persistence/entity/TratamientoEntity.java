package com.odontomax.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "tratamientos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TratamientoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "paciente_id", nullable = false)
    private UUID pacienteId;

    @Column(name = "sede_id", nullable = false)
    private String sedeId;

    @Column(name = "especialista_id")
    private UUID especialistaId;

    @Column(name = "procedimiento_id")
    private String procedimientoId;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    @Column(name = "pieza_dental")
    private String piezaDental;

    @Column(name = "diagnostico")
    private String diagnostico;

    @Column(name = "modalidad_pago", nullable = false)
    @Builder.Default
    private String modalidadPago = "CONTADO";

    @Column(name = "costo_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal costoTotal;

    @Column(name = "descuento_total", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal descuentoTotal = BigDecimal.ZERO;

    @Column(name = "descuento_autorizado_por")
    private UUID descuentoAutorizadoPor;

    @Column(name = "estado", nullable = false)
    @Builder.Default
    private String estado = "PLANIFICADO";

    @Column(name = "aceptado_por_paciente", nullable = false)
    @Builder.Default
    private Boolean aceptadoPorPaciente = false;

    @Column(name = "fecha_aceptacion")
    private Instant fechaAceptacion;

    @Column(name = "fecha_inicio")
    private LocalDate fechaInicio;

    @Column(name = "fecha_fin_estimada")
    private LocalDate fechaFinEstimada;

    @Column(name = "total_sesiones", nullable = false)
    @Builder.Default
    private Integer totalSesiones = 1;

    @Column(name = "sesiones_realizadas", nullable = false)
    @Builder.Default
    private Integer sesionesRealizadas = 0;

    @Column(name = "proximo_control")
    private LocalDate proximoControl;

    @Column(name = "requiere_seguimiento", nullable = false)
    @Builder.Default
    private Boolean requiereSeguimiento = false;

    @Column(name = "observaciones")
    private String observaciones;

    @Column(name = "motivo_cierre")
    private String motivoCierre;

    @Column(name = "cerrado_en")
    private Instant cerradoEn;

    @Column(name = "precio_catalogo", precision = 10, scale = 2)
    private BigDecimal precioCatalogo;

    @Column(name = "motivo_ajuste_precio")
    private String motivoAjustePrecio;

    @Column(name = "ajuste_precio_por")
    private UUID ajustePrecioPor;

    @Column(name = "pagado_antes_sistema", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal pagadoAntesSistema = BigDecimal.ZERO;

    @OneToMany(mappedBy = "tratamiento", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<TratamientoItemEntity> items = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "creado_en", updatable = false)
    private Instant creadoEn;

    @UpdateTimestamp
    @Column(name = "actualizado_en")
    private Instant actualizadoEn;
}
