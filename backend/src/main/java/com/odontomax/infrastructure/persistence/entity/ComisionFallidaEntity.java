package com.odontomax.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Una comisión que no se pudo generar al cobrar un estudio; queda hasta que se genere o se descarte. */
@Entity
@Table(name = "comisiones_fallidas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ComisionFallidaEntity {

    @Id
    @Column(name = "cita_id")
    private UUID citaId;

    @Column(name = "sede_id", nullable = false)
    private String sedeId;

    @Column(name = "paciente_nombre")
    private String pacienteNombre;

    private String estudio;

    @Column(nullable = false)
    private String motivo;

    @Column(nullable = false)
    @Builder.Default
    private Integer intentos = 1;

    @Column(name = "creado_en", nullable = false)
    @Builder.Default
    private OffsetDateTime creadoEn = OffsetDateTime.now();

    @Column(name = "actualizado_en", nullable = false)
    @Builder.Default
    private OffsetDateTime actualizadoEn = OffsetDateTime.now();
}
