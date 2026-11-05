package com.odontomax.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "caja_sesiones")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CajaSesionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "sede_id", nullable = false)
    private String sedeId;

    @Column(name = "abierta_por", nullable = false)
    private UUID abiertaPor;

    @CreationTimestamp
    @Column(name = "abierta_en", updatable = false)
    private Instant abiertaEn;

    @Column(name = "monto_apertura", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal montoApertura = BigDecimal.ZERO;

    @Column(name = "cerrada_por")
    private UUID cerradaPor;

    @Column(name = "cerrada_en")
    private Instant cerradaEn;

    @Column(name = "monto_esperado", precision = 10, scale = 2)
    private BigDecimal montoEsperado;

    @Column(name = "monto_contado", precision = 10, scale = 2)
    private BigDecimal montoContado;

    @Column(name = "monto_esperado_yape", precision = 10, scale = 2)
    private BigDecimal montoEsperadoYape;

    @Column(name = "monto_contado_yape", precision = 10, scale = 2)
    private BigDecimal montoContadoYape;

    @Column(name = "monto_esperado_plin", precision = 10, scale = 2)
    private BigDecimal montoEsperadoPlin;

    @Column(name = "monto_contado_plin", precision = 10, scale = 2)
    private BigDecimal montoContadoPlin;

    @Column(name = "monto_esperado_tarjeta", precision = 10, scale = 2)
    private BigDecimal montoEsperadoTarjeta;

    @Column(name = "monto_contado_tarjeta", precision = 10, scale = 2)
    private BigDecimal montoContadoTarjeta;

    @Column(name = "diferencia", insertable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal diferencia;

    @Column(name = "observaciones")
    private String observaciones;

    @OneToMany(mappedBy = "sesion", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<CajaMovimientoEntity> movimientos = new ArrayList<>();
}
