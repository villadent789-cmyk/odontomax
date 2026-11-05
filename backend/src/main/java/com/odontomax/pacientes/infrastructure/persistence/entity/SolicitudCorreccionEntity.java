package com.odontomax.pacientes.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "solicitudes_correccion_paciente")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SolicitudCorreccionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "paciente_id", nullable = false)
    private UUID pacienteId;

    @Column(name = "sede_id", nullable = false)
    private String sedeId;

    @Column(name = "nombres_anterior", nullable = false)
    private String nombresAnterior;

    @Column(name = "apellidos_anterior", nullable = false)
    private String apellidosAnterior;

    @Column(name = "dni_anterior", nullable = false)
    private String dniAnterior;

    @Column(name = "fecha_nacimiento_anterior")
    private LocalDate fechaNacimientoAnterior;

    @Column(name = "nombres_nuevo")
    private String nombresNuevo;

    @Column(name = "apellidos_nuevo")
    private String apellidosNuevo;

    @Column(name = "dni_nuevo")
    private String dniNuevo;

    @Column(name = "fecha_nacimiento_nueva")
    private LocalDate fechaNacimientoNueva;

    @Column(nullable = false, columnDefinition = "text")
    private String motivo;

    @Column(nullable = false)
    private String estado;

    @Column(name = "solicitado_por", nullable = false)
    private UUID solicitadoPor;

    @Column(name = "solicitado_por_nombre", nullable = false)
    private String solicitadoPorNombre;

    @Builder.Default
    @Column(name = "solicitado_en", nullable = false, updatable = false)
    private OffsetDateTime solicitadoEn = OffsetDateTime.now();

    @Column(name = "resuelto_por")
    private UUID resueltoPor;

    @Column(name = "resuelto_por_nombre")
    private String resueltoPorNombre;

    @Column(name = "resuelto_en")
    private OffsetDateTime resueltoEn;

    @Column(name = "comentario_resolucion", columnDefinition = "text")
    private String comentarioResolucion;
}
