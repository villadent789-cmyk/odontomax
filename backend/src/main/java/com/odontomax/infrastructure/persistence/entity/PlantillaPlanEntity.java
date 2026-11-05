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

/** Un plan de tratamiento que se repite: servicio del catálogo, piezas, gravedad y sesiones. */
@Entity
@Table(name = "plantillas_plan")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlantillaPlanEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "unidad_de_negocio", nullable = false)
    private String unidadDeNegocio;

    @Column(nullable = false)
    private String nombre;

    @Column(name = "procedimiento_id", nullable = false)
    private String procedimientoId;

    @Column(name = "servicio_nombre", nullable = false)
    private String servicioNombre;

    @Builder.Default
    @Column(nullable = false)
    private int piezas = 1;

    private String gravedad;

    @Builder.Default
    @Column(nullable = false)
    private int sesiones = 1;

    private String notas;

    @Builder.Default
    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "creado_por")
    private String creadoPor;

    @Builder.Default
    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn = Instant.now();
}
