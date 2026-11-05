package com.odontomax.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "inventario_stock")
@IdClass(InventarioStockId.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventarioStockEntity {

    @Id
    @Column(name = "producto_id", nullable = false)
    private UUID productoId;

    @Id
    @Column(name = "sede_id", nullable = false)
    private String sedeId;

    @Builder.Default
    @Column(nullable = false)
    private Integer cantidad = 0;
}
