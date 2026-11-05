package com.odontomax.pacientes.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "plantillas_consentimiento")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlantillaConsentimientoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String codigo;

    @Column(nullable = false)
    private String titulo;

    @Column(nullable = false, length = 50)
    private String tipo; // TRATAMIENTO_DATOS, INFORMADO_PROCEDIMIENTO, USO_IMAGEN

    @Column(nullable = false, columnDefinition = "text")
    private String contenido;

    @Builder.Default
    @Column(nullable = false)
    private Integer version = 1;

    @Builder.Default
    @Column(nullable = false)
    private Boolean activo = true;

    @Builder.Default
    @Column(name = "creado_en", updatable = false)
    private OffsetDateTime creadoEn = OffsetDateTime.now();

    @PrePersist
    public void prePersist() {
        if (this.creadoEn == null) {
            this.creadoEn = OffsetDateTime.now();
        }
        if (this.activo == null) {
            this.activo = true;
        }
        if (this.version == null) {
            this.version = 1;
        }
    }
}
