package com.odontomax.pacientes.infrastructure.persistence.repository;

import com.odontomax.pacientes.infrastructure.persistence.entity.FichaIngresoEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SpringDataFichaIngresoRepository extends JpaRepository<FichaIngresoEntity, UUID> {
}
