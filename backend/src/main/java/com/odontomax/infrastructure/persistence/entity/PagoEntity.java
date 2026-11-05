package com.odontomax.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pagos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PagoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "sede_id", nullable = false)
    private String sedeId;

    @Column(name = "paciente_id", nullable = false)
    private UUID pacienteId;

    @Column(name = "tratamiento_id")
    private UUID tratamientoId;

    @Column(name = "monto", nullable = false, precision = 10, scale = 2)
    private BigDecimal monto;

    @Column(name = "metodo_pago", nullable = false)
    private String metodoPago; // "EFECTIVO", "YAPE"

    @Column(name = "numero_operacion")
    private String numeroOperacion;

    @Column(name = "comprobante")
    private String comprobante;

    @Column(name = "comprobante_tipo")
    @Builder.Default
    private String comprobanteTipo = "RECIBO";

    @Column(name = "comprobante_serie")
    private String comprobanteSerie;

    @Column(name = "comprobante_numero")
    private String comprobanteNumero;

    @Column(name = "comprobante_hash")
    private String comprobanteHash;

    @Column(name = "caja_sesion_id")
    private UUID cajaSesionId;

    @Column(name = "especialista_id")
    private UUID especialistaId;

    @CreationTimestamp
    @Column(name = "pagado_en", updatable = false)
    private Instant pagadoEn;

    @Column(name = "registrado_por")
    private UUID registradoPor;

    @Column(name = "anulado", nullable = false)
    @Builder.Default
    private Boolean anulado = false;

    @Column(name = "anulado_en")
    private Instant anuladoEn;

    @Column(name = "anulado_por")
    private UUID anuladoPor;

    @Column(name = "motivo_anulacion")
    private String motivoAnulacion;

    @CreationTimestamp
    @Column(name = "creado_en", updatable = false)
    private Instant creadoEn;
}
