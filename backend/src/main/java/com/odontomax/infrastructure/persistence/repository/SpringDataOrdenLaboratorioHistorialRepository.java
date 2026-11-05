package com.odontomax.infrastructure.persistence.repository;

import com.odontomax.infrastructure.persistence.entity.OrdenLaboratorioHistorialEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SpringDataOrdenLaboratorioHistorialRepository extends JpaRepository<OrdenLaboratorioHistorialEntity, Long> {
    List<OrdenLaboratorioHistorialEntity> findByOrdenIdOrderByCreadoEnAsc(UUID ordenId);
}
