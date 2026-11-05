package com.odontomax.infrastructure.persistence.repository;

import com.odontomax.infrastructure.persistence.entity.ProductoInventarioEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpringDataProductoInventarioRepository extends JpaRepository<ProductoInventarioEntity, UUID> {

    List<ProductoInventarioEntity> findByActivoTrueOrderByNombreAsc();

    Optional<ProductoInventarioEntity> findByCodigoIgnoreCase(String codigo);

    boolean existsByCodigoIgnoreCase(String codigo);
}
