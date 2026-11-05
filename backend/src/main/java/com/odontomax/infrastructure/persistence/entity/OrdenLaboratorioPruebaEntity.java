package com.odontomax.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Una prueba del trabajo de laboratorio con el paciente: prueba 1, prueba 2… hasta que queda correcto. */
@Entity
@Table(name = "ordenes_laboratorio_pruebas")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrdenLaboratorioPruebaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "orden_id", nullable = false)
    private UUID ordenId;

    @Column(nullable = false)
    private int numero;

    @Builder.Default
    @Column(nullable = false)
    private LocalDate fecha = LocalDate.now();

    /** NECESITA_AJUSTE (vuelve al laboratorio) o CORRECTO (se finaliza el trabajo). */
    @Column(nullable = false)
    private String resultado;

    private String notas;

    @Column(name = "registrado_por")
    private String registradoPor;

    @Builder.Default
    @Column(name = "creado_en", updatable = false)
    private OffsetDateTime creadoEn = OffsetDateTime.now();
}
