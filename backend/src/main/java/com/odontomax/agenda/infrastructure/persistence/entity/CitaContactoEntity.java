package com.odontomax.agenda.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "cita_contactos")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CitaContactoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "cita_id", nullable = false)
    private UUID citaId;

    @Column(nullable = false, length = 20)
    private String medio; // WHATSAPP, LLAMADA

    @Column(nullable = false, length = 50)
    private String resultado; // CONFIRMO, PIDIO_REPROGRAMAR, CANCELO, NO_RESPONDE, MENSAJE_ENVIADO

    @Column(columnDefinition = "text")
    private String notas;

    @Column(nullable = false)
    private String usuario;

    @Builder.Default
    @Column(name = "creado_en", updatable = false)
    private OffsetDateTime creadoEn = OffsetDateTime.now();

    @PrePersist
    public void prePersist() {
        if (this.creadoEn == null) {
            this.creadoEn = OffsetDateTime.now();
        }
    }
}
