package com.odontomax.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "radiografias_estudios")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RadiografiaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "paciente_id", nullable = false)
    private UUID pacienteId;

    @Column(name = "doctor_id")
    private UUID doctorId;

    @Column(name = "tipo_estudio", nullable = false, length = 30)
    private String tipoEstudio; // PANORAMICA, PERIAPICAL, ALETA_MORDIDA, CEFALOMETRICA, FOTO_ANTES, FOTO_DESPUES

    @Column(name = "pieza_fdi")
    private Integer piezaFdi;

    @Column(length = 20)
    private String etapa; // INICIAL, CONTROL, FINAL

    @Column(name = "archivo_url", nullable = false, columnDefinition = "text")
    private String archivoUrl;

    @Column(name = "nombre_archivo", length = 255)
    private String nombreArchivo;

    @Column(name = "tamano_bytes")
    private Long tamanoBytes;

    @Column(name = "diagnostico_radiografico", columnDefinition = "text")
    private String diagnosticoRadiografico;

    @Column(name = "fecha_toma", nullable = false)
    private LocalDate fechaToma;

    @Builder.Default
    @Column(name = "creado_en", updatable = false)
    private OffsetDateTime creadoEn = OffsetDateTime.now();
}
