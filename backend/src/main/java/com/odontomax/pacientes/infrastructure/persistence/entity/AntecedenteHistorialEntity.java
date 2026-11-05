package com.odontomax.pacientes.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "antecedentes_historial")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AntecedenteHistorialEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "paciente_id", nullable = false)
    private UUID pacienteId;

    @Column(columnDefinition = "text")
    private String alergias;

    @Column(name = "antecedentes_medicos", columnDefinition = "text")
    private String antecedentesMedicos;

    @Column(name = "es_gestante")
    private Boolean gestante;

    @Column(name = "semanas_gestacion")
    private Integer semanasGestacion;

    @Column(name = "motivo_cambio", nullable = false, columnDefinition = "text")
    private String motivoCambio;

    @Column(name = "modificado_por")
    private UUID modificadoPor;

    @Column(name = "modificado_por_nombre")
    private String modificadoPorNombre;

    @Builder.Default
    @Column(name = "creado_en", updatable = false)
    private OffsetDateTime creadoEn = OffsetDateTime.now();

    @PrePersist
    public void prePersist() {
        if (this.creadoEn == null) {
            this.creadoEn = OffsetDateTime.now();
        }
    }
}
