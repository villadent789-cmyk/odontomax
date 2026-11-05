package com.odontomax.infrastructure.persistence.repository;

import com.odontomax.infrastructure.persistence.entity.PlanCuotaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SpringDataPlanCuotaRepository extends JpaRepository<PlanCuotaEntity, UUID> {

    List<PlanCuotaEntity> findByTratamientoIdOrderByNumeroAsc(UUID tratamientoId);
}
