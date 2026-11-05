package com.odontomax.infrastructure.persistence.entity;

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
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pagos_laboratorio")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagoLaboratorioEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "orden_id", nullable = false)
    private UUID ordenId;

    @Column(name = "laboratorio_id", nullable = false)
    private UUID laboratorioId;

    @Column(name = "sede_id", nullable = false)
    private String sedeId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal monto;

    @Column(name = "metodo_pago", nullable = false)
    private String metodoPago;

    @Column(name = "numero_operacion")
    private String numeroOperacion;

    @CreationTimestamp
    @Column(name = "pagado_en", nullable = false, updatable = false)
    private Instant pagadoEn;

    @Column(name = "caja_sesion_id")
    private UUID cajaSesionId;

    @Column(name = "registrado_por")
    private UUID registradoPor;

    @Column(columnDefinition = "TEXT")
    private String notas;

    @Builder.Default
    @Column(name = "anulado", nullable = false)
    private Boolean anulado = false;

    @Column(name = "anulado_por")
    private UUID anuladoPor;

    @Column(name = "anulado_en")
    private Instant anuladoEn;

    @Column(name = "motivo_anulacion", columnDefinition = "TEXT")
    private String motivoAnulacion;
}
