package com.odontomax.infrastructure.persistence.repository;

import com.odontomax.infrastructure.persistence.entity.RadiografiaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SpringDataRadiografiaRepository extends JpaRepository<RadiografiaEntity, UUID> {

    List<RadiografiaEntity> findByPacienteIdOrderByFechaTomaDesc(UUID pacienteId);

    List<RadiografiaEntity> findByPacienteIdAndTipoEstudioOrderByFechaTomaDesc(UUID pacienteId, String tipoEstudio);
}
