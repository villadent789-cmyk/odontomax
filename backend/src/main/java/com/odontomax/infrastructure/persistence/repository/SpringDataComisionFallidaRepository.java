package com.odontomax.infrastructure.persistence.repository;

import com.odontomax.infrastructure.persistence.entity.ComisionFallidaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SpringDataComisionFallidaRepository extends JpaRepository<ComisionFallidaEntity, UUID> {

    List<ComisionFallidaEntity> findAllByOrderByCreadoEnAsc();
}
