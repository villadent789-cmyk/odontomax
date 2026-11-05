package com.odontomax.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "notas_evolucion")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotaEvolucionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "paciente_id", nullable = false)
    private UUID pacienteId;

    @Column(name = "cita_id")
    private UUID citaId;

    @Column(name = "autor_id", nullable = false)
    private UUID autorId;

    @Column(name = "autor_nombre", nullable = false)
    private String autorNombre;

    @Column(name = "autor_rol", nullable = false, length = 50)
    private String autorRol;

    @Builder.Default
    @Column(name = "tipo_nota", nullable = false, length = 20)
    private String tipoNota = "SOAP";

    @Column(columnDefinition = "text")
    private String subjetivo;

    @Column(columnDefinition = "text")
    private String objetivo;

    @Column(columnDefinition = "text")
    private String analisis;

    @Column(columnDefinition = "text")
    private String plan;

    @Column(columnDefinition = "text")
    private String contenido;

    @Column(name = "nota_referencia_id")
    private UUID notaReferenciaId;

    @Column(name = "motivo_correccion", columnDefinition = "text")
    private String motivoCorreccion;

    @Builder.Default
    @Column(nullable = false)
    private Boolean firmado = true;

    @Builder.Default
    @Column(name = "creado_en", updatable = false)
    private OffsetDateTime creadoEn = OffsetDateTime.now();

    @PrePersist
    public void prePersist() {
        if (this.creadoEn == null) {
            this.creadoEn = OffsetDateTime.now();
        }
        if (this.firmado == null) {
            this.firmado = true;
        }
        if (this.tipoNota == null || this.tipoNota.isBlank()) {
            this.tipoNota = "SOAP";
        }
    }
}
