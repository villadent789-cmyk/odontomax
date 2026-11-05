package com.odontomax.pacientes.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "solicitudes_eliminacion")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SolicitudEliminacionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tipo", nullable = false)
    private String tipo;

    @Column(name = "objetivo_id", nullable = false)
    private UUID objetivoId;

    @Column(name = "paciente_id", nullable = false)
    private UUID pacienteId;

    @Column(name = "sede_id", nullable = false)
    private String sedeId;

    @Column(name = "descripcion", nullable = false)
    private String descripcion;

    @Column(name = "motivo", nullable = false)
    private String motivo;

    @Column(name = "estado", nullable = false)
    private String estado;

    @Column(name = "solicitado_por", nullable = false)
    private UUID solicitadoPor;

    @Column(name = "solicitado_por_nombre", nullable = false)
    private String solicitadoPorNombre;

    @Column(name = "solicitado_en", nullable = false)
    @Builder.Default
    private OffsetDateTime solicitadoEn = OffsetDateTime.now();

    @Column(name = "resuelto_por")
    private UUID resueltoPor;

    @Column(name = "resuelto_por_nombre")
    private String resueltoPorNombre;

    @Column(name = "resuelto_en")
    private OffsetDateTime resueltoEn;

    @Column(name = "comentario_resolucion")
    private String comentarioResolucion;
}
