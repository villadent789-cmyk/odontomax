package com.odontomax.pacientes.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Descuento pendiente de quien recomendó a un paciente nuevo. */
@Entity
@Table(name = "descuentos_recomendacion")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DescuentoRecomendacionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Paciente que recomendó y recibe el descuento. */
    @Column(name = "paciente_id", nullable = false)
    private UUID pacienteId;

    /** Paciente nuevo que llegó por la recomendación. */
    @Column(name = "referido_id", nullable = false)
    private UUID referidoId;

    @Builder.Default
    @Column(nullable = false)
    private String estado = "PENDIENTE";

    @Builder.Default
    @Column(name = "creado_en", nullable = false, updatable = false)
    private OffsetDateTime creadoEn = OffsetDateTime.now();

    @Column(name = "aplicado_en")
    private OffsetDateTime aplicadoEn;

    @Column(name = "aplicado_por")
    private String aplicadoPor;

    @Column(name = "tratamiento_id")
    private UUID tratamientoId;
}
