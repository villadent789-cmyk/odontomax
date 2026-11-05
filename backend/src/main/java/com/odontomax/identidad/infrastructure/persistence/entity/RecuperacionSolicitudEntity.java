package com.odontomax.identidad.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "seguridad_recuperacion_solicitudes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RecuperacionSolicitudEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "clave", length = 255, nullable = false)
    private String clave;

    @Column(name = "solicitado_en", nullable = false)
    private long solicitadoEn;

    @Column(name = "expira_en", nullable = false)
    private Instant expiraEn;

    public RecuperacionSolicitudEntity(String clave, long solicitadoEn, Instant expiraEn) {
        this.clave = clave;
        this.solicitadoEn = solicitadoEn;
        this.expiraEn = expiraEn;
    }
}
