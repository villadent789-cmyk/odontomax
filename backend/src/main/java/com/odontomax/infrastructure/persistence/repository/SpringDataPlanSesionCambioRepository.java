package com.odontomax.infrastructure.persistence.repository;

import com.odontomax.infrastructure.persistence.entity.PlanSesionCambioEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SpringDataPlanSesionCambioRepository extends JpaRepository<PlanSesionCambioEntity, UUID> {

    List<PlanSesionCambioEntity> findByTratamientoIdOrderByCreadoEnAsc(UUID tratamientoId);
}
