package com.odontomax.infrastructure.persistence.repository;

import com.odontomax.infrastructure.persistence.entity.InventarioStockEntity;
import com.odontomax.infrastructure.persistence.entity.InventarioStockId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpringDataInventarioStockRepository extends JpaRepository<InventarioStockEntity, InventarioStockId> {

    List<InventarioStockEntity> findByProductoId(UUID productoId);

    List<InventarioStockEntity> findBySedeId(String sedeId);

    Optional<InventarioStockEntity> findByProductoIdAndSedeId(UUID productoId, String sedeId);
}
