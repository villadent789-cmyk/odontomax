package com.odontomax.agenda.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "lista_espera_huecos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EsperaHuecoEntity {

    @Id
    private UUID id;

    @Column(name = "sede_id", nullable = false)
    private String sedeId;

    @Column(name = "paciente_id", nullable = false)
    private UUID pacienteId;

    @Column(name = "paciente_nombre", nullable = false)
    private String pacienteNombre;

    @Column(name = "paciente_telefono")
    private String pacienteTelefono;

    @Column(name = "especialista_id")
    private UUID especialistaId;

    @Column(nullable = false)
    private String motivo;

    private String notas;

    @Column(nullable = false)
    private String estado;

    @Column(name = "creado_por")
    private UUID creadoPor;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;
}
