package com.odontomax.pacientes.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "paciente_fotos")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FotoPacienteEntity {

    @Id
    @Column(name = "paciente_id")
    private UUID pacienteId;

    @Column(name = "contenido", nullable = false)
    private byte[] contenido;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Builder.Default
    @Column(name = "actualizado_en", nullable = false)
    private OffsetDateTime actualizadoEn = OffsetDateTime.now();

    @Column(name = "actualizado_por")
    private String actualizadoPor;
}
