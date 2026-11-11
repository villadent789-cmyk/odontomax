package com.odontomax.agenda.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "especialistas")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EspecialistaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "usuario_id", unique = true)
    private UUID usuarioId;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private String especialidad;

    private String cop;

    private String email;

    private String telefono;

    @Column(name = "foto_url")
    private String fotoUrl;

    @Builder.Default
    @Column(nullable = false)
    private boolean activo = true;

    @Column(nullable = false)
    @Builder.Default
    private boolean invitado = false;

    @Column(name = "meta_mensual", precision = 10, scale = 2)
    private BigDecimal metaMensual;

    @Column(name = "comision_excedente_porcentaje", precision = 5, scale = 2)
    private BigDecimal comisionExcedentePorcentaje;
}
