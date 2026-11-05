package com.odontomax.infrastructure.persistence.repository;

import com.odontomax.infrastructure.persistence.entity.InventarioMovimientoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface SpringDataInventarioMovimientoRepository extends JpaRepository<InventarioMovimientoEntity, Long> {

    List<InventarioMovimientoEntity> findByProductoIdOrderByCreadoEnDesc(UUID productoId);

    List<InventarioMovimientoEntity> findBySedeIdOrderByCreadoEnDesc(String sedeId);

    /** Por producto: [productoId, próximo vencimiento desde hoy, última fecha de entrega] de sus ENTRADAS (sedeId null = todas las sedes). */
    @Query("SELECT m.productoId, MIN(CASE WHEN m.fechaVencimiento >= :hoy THEN m.fechaVencimiento END), MAX(m.fechaEntrega) " +
           "FROM InventarioMovimientoEntity m WHERE m.tipo = 'ENTRADA' AND (:sedeId IS NULL OR m.sedeId = :sedeId) " +
           "GROUP BY m.productoId")
    List<Object[]> resumenFechasPorProducto(@Param("hoy") LocalDate hoy, @Param("sedeId") String sedeId);
}
