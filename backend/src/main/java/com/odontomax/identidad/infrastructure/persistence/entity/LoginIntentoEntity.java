package com.odontomax.identidad.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "seguridad_login_intentos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LoginIntentoEntity {

    @Id
    @Column(name = "clave", length = 255, nullable = false)
    private String clave;

    @Column(name = "intentos", nullable = false)
    private int intentos;

    @Column(name = "primer_intento", nullable = false)
    private long primerIntento;

    @Column(name = "expira_en", nullable = false)
    private Instant expiraEn;
}
