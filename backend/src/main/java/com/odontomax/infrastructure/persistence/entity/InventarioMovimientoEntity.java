package com.odontomax.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "inventario_movimientos")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventarioMovimientoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "producto_id", nullable = false)
    private UUID productoId;

    @Column(name = "sede_id", nullable = false)
    private String sedeId;

    @Column(nullable = false, length = 30)
    private String tipo; // ENTRADA, SALIDA, AJUSTE, TRANSFERENCIA_ENTRADA, TRANSFERENCIA_SALIDA

    @Column(nullable = false)
    private Integer cantidad; // positivo para entrada/ajuste +, negativo para salida

    private String lote;

    @Column(name = "fecha_vencimiento")
    private LocalDate fechaVencimiento;

    @Column(name = "fecha_entrega")
    private LocalDate fechaEntrega;

    private String proveedor;

    private String motivo;

    @Column(name = "registrado_por")
    private UUID registradoPor;

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
