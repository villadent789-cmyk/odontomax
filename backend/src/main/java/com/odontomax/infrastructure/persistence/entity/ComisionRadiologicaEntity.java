package com.odontomax.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "comisiones_radiologicas")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComisionRadiologicaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "doctor_derivador_id", nullable = false)
    private UUID doctorDerivadorId;

    @Column(name = "paciente_nombre", nullable = false)
    private String pacienteNombre;

    @Column(name = "paciente_id")
    private UUID pacienteId;

    @Column(name = "cita_id")
    private UUID citaId;

    @Column(name = "tratamiento_id")
    private UUID tratamientoId;

    @Column(name = "tipo_estudio", nullable = false)
    private String tipoEstudio;

    @Column(name = "precio_cobrado", nullable = false)
    private BigDecimal precioCobrado;

    @Column(name = "monto_comision", nullable = false)
    private BigDecimal montoComision;

    /** Cuántos estudios cubre este registro (10 radiografías = 10 × la comisión fija). */
    @Builder.Default
    @Column(nullable = false)
    private int cantidad = 1;

    /** Paciente estudiante con convenio: precio manual y sin comisión. */
    @Builder.Default
    @Column(name = "es_estudiante", nullable = false)
    private boolean esEstudiante = false;

    @Builder.Default
    @Column(nullable = false)
    private String estado = "PENDIENTE";

    @Builder.Default
    @Column(name = "fecha_emision", nullable = false)
    private OffsetDateTime fechaEmision = OffsetDateTime.now();

    @Column(name = "fecha_liquidacion")
    private OffsetDateTime fechaLiquidacion;

    @Column(name = "metodo_pago")
    private String metodoPago;

    @Column(name = "codigo_operacion")
    private String codigoOperacion;

    /** Las comisiones liquidadas juntas comparten este identificador (y un solo egreso de caja). */
    @Column(name = "lote_liquidacion_id")
    private UUID loteLiquidacionId;

    @Column(columnDefinition = "TEXT")
    private String nota;

    @Builder.Default
    @Column(name = "sede_id")
    private String sedeId = "sede-villamax-chilca";

    @Column(name = "registrado_por")
    private String registradoPor;

    @Builder.Default
    @Column(name = "creado_en", updatable = false)
    private OffsetDateTime creadoEn = OffsetDateTime.now();

    @Builder.Default
    @Column(name = "actualizado_en")
    private OffsetDateTime actualizadoEn = OffsetDateTime.now();

    @PrePersist
    public void prePersist() {
        if (this.fechaEmision == null) {
            this.fechaEmision = OffsetDateTime.now();
        }
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
