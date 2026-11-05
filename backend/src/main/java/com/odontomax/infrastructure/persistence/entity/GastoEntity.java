package com.odontomax.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "gastos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GastoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "sede_id", nullable = false)
    private String sedeId;

    @Column(name = "categoria", nullable = false)
    private String categoria;

    @Column(name = "concepto", nullable = false)
    private String concepto;

    @Column(name = "monto", nullable = false, precision = 10, scale = 2)
    private BigDecimal monto;

    @Column(name = "metodo_pago", nullable = false)
    private String metodoPago;

    @Column(name = "comprobante")
    private String comprobante;

    @Column(name = "comprobante_key")
    private String comprobanteKey;

    @Column(name = "caja_sesion_id")
    private UUID cajaSesionId;

    @CreationTimestamp
    @Column(name = "gastado_en", updatable = false)
    private Instant gastadoEn;

    @Column(name = "registrado_por")
    private UUID registradoPor;

    @Column(name = "anulado", nullable = false)
    @Builder.Default
    private Boolean anulado = false;

    @Column(name = "motivo_anulacion")
    private String motivoAnulacion;

    @CreationTimestamp
    @Column(name = "creado_en", updatable = false)
    private Instant creadoEn;
}
