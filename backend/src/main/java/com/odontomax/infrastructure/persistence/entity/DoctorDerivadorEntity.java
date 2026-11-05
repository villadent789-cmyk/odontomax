package com.odontomax.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "doctores_derivadores")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DoctorDerivadorEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String nombre;

    private String cop;

    @Column(name = "clinica_origen")
    private String clinicaOrigen;

    private String telefono;

    private String banco;

    @Column(name = "numero_cuenta_o_yape")
    private String numeroCuentaOYape;

    @Builder.Default
    @Column(name = "porcentaje_o_tarifa_fija")
    private BigDecimal porcentajeOTarifaFija = BigDecimal.valueOf(10.00);

    @Builder.Default
    @Column(nullable = false)
    private boolean activo = true;

    @Column(columnDefinition = "TEXT")
    private String notas;

    @Builder.Default
    @Column(name = "creado_en", updatable = false)
    private OffsetDateTime creadoEn = OffsetDateTime.now();

    @Builder.Default
    @Column(name = "actualizado_en")
    private OffsetDateTime actualizadoEn = OffsetDateTime.now();

    @PrePersist
    public void prePersist() {
        if (this.creadoEn == null) {
            this.creadoEn = OffsetDateTime.now();
        }
        if (this.actualizadoEn == null) {
            this.actualizadoEn = OffsetDateTime.now();
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.actualizadoEn = OffsetDateTime.now();
    }
}
