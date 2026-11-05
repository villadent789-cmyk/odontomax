package com.odontomax.pacientes.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "historia_clinica_accesos")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistoriaClinicaAccesoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "paciente_id", nullable = false)
    private UUID pacienteId;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(name = "usuario_nombre", nullable = false)
    private String usuarioNombre;

    @Column(name = "usuario_email", nullable = false)
    private String usuarioEmail;

    @Column(name = "usuario_rol", nullable = false)
    private String usuarioRol;

    @Column(name = "sede_id", nullable = false)
    private String sedeId;

    @Column(nullable = false, length = 45)
    private String ip;

    @Builder.Default
    @Column(name = "accedido_en", updatable = false)
    private OffsetDateTime accedidoEn = OffsetDateTime.now();

    @PrePersist
    public void prePersist() {
        if (this.accedidoEn == null) {
            this.accedidoEn = OffsetDateTime.now();
        }
    }
}
