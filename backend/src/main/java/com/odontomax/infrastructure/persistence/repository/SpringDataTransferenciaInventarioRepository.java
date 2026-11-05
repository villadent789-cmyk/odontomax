package com.odontomax.infrastructure.persistence.repository;

import com.odontomax.infrastructure.persistence.entity.TransferenciaInventarioEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SpringDataTransferenciaInventarioRepository extends JpaRepository<TransferenciaInventarioEntity, UUID> {

    List<TransferenciaInventarioEntity> findAllByOrderByCreadoEnDesc();

    @Query("SELECT t FROM TransferenciaInventarioEntity t WHERE t.sedeOrigenId = :sedeId OR t.sedeDestinoId = :sedeId ORDER BY t.creadoEn DESC")
    List<TransferenciaInventarioEntity> findBySede(@Param("sedeId") String sedeId);
}
