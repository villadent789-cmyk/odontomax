package com.odontomax.infrastructure.persistence.entity;

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

import java.time.Instant;
import java.util.UUID;

/** Cambio del número de sesiones de un plan, con su motivo. */
@Entity
@Table(name = "tratamiento_sesiones_cambios")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanSesionCambioEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tratamiento_id", nullable = false)
    private UUID tratamientoId;

    @Column(name = "sesiones_antes", nullable = false)
    private int sesionesAntes;

    @Column(name = "sesiones_despues", nullable = false)
    private int sesionesDespues;

    @Column(nullable = false)
    private String motivo;

    @Column(name = "usuario_id")
    private UUID usuarioId;

    @Column(name = "usuario_nombre")
    private String usuarioNombre;

    @Builder.Default
    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn = Instant.now();
}
