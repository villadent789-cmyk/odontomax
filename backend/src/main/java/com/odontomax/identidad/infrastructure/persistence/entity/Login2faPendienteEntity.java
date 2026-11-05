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
@Table(name = "seguridad_login_2fa_pendientes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Login2faPendienteEntity {

    @Id
    @Column(name = "clave", length = 255, nullable = false)
    private String clave;

    @Column(name = "intentos", nullable = false)
    private int intentos;

    @Column(name = "invalidado", nullable = false)
    private boolean invalidado;

    @Column(name = "creado_en", nullable = false)
    private long creadoEn;

    @Column(name = "expira_en", nullable = false)
    private Instant expiraEn;
}
