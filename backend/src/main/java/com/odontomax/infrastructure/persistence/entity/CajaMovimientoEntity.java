package com.odontomax.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "caja_movimientos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CajaMovimientoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sesion_id", nullable = false)
    private CajaSesionEntity sesion;

    @Column(name = "tipo", nullable = false)
    private String tipo; // "INGRESO", "EGRESO"

    @Column(name = "concepto", nullable = false)
    private String concepto;

    @Column(name = "monto", nullable = false, precision = 10, scale = 2)
    private BigDecimal monto;

    @Column(name = "metodo_pago", nullable = false)
    @Builder.Default
    private String metodoPago = "EFECTIVO";

    @Column(name = "referencia_tipo")
    private String referenciaTipo; // "PAGO_TRATAMIENTO", "GASTO", "AJUSTE_MANUAL", "OTRO"

    @Column(name = "referencia_id")
    private UUID referenciaId;

    @Column(name = "registrado_por", nullable = false)
    private UUID registradoPor;

    @CreationTimestamp
    @Column(name = "registrado_en", updatable = false)
    private Instant registradoEn;
}
