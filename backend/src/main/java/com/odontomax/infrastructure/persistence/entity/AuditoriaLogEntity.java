package com.odontomax.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnTransformer;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auditoria_log")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditoriaLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "creado_en", insertable = false, updatable = false)
    private Instant creadoEn;

    @Column(name = "usuario_id")
    private UUID usuarioId;

    @Column(name = "usuario_nombre")
    private String usuarioNombre;

    @Column(name = "rol")
    private String rol;

    @Column(name = "sede_id")
    private String sedeId;

    @Column(name = "modulo", nullable = false)
    private String modulo;

    @Column(name = "accion", nullable = false)
    private String accion;

    @Column(name = "tabla")
    private String tabla;

    @Column(name = "registro_id")
    private String registroId;

    @Column(name = "detalle")
    private String detalle;

    @Column(name = "ip")
    @ColumnTransformer(read = "ip::text", write = "?::inet")
    private String ip;
}
