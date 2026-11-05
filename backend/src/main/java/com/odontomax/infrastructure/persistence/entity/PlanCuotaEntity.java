package com.odontomax.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Una cuota del cronograma de un plan. Lo pagado se reparte desde los cobros: no se guarda aquí. */
@Entity
@Table(name = "plan_cuotas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlanCuotaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "tratamiento_id", nullable = false)
    private UUID tratamientoId;

    @Column(nullable = false)
    private Integer numero;

    @Column(nullable = false)
    private String concepto;

    @Column(name = "vence_en", nullable = false)
    private LocalDate venceEn;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal monto;
}
