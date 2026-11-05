package com.odontomax.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "odontograma_registros")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OdontogramaRegistroEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "paciente_id", nullable = false)
    private UUID pacienteId;

    @Column(name = "pieza_fdi", nullable = false)
    private Integer piezaFdi;

    @Column(nullable = false, length = 50)
    private String estado;

    @Column(name = "caras", columnDefinition = "text[]")
    private String[] caras;

    @Column(columnDefinition = "text")
    private String observacion;

    /** Falso cuando el registro quita ese hallazgo de la pieza. */
    @Builder.Default
    @Column(name = "activo", nullable = false)
    private boolean activo = true;

    @Column(name = "especialista_id")
    private UUID especialistaId;

    @Column(name = "registrado_por")
    private UUID registradoPor;

    @Builder.Default
    @Column(name = "registrado_en", updatable = false)
    private OffsetDateTime registradoEn = OffsetDateTime.now();

    @PrePersist
    public void prePersist() {
        if (this.registradoEn == null) {
            this.registradoEn = OffsetDateTime.now();
        }
        if (this.caras == null) {
            this.caras = new String[0];
        }
    }
}
