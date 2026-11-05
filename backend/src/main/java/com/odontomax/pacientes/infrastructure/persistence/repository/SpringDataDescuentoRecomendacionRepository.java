package com.odontomax.pacientes.infrastructure.persistence.repository;

import com.odontomax.pacientes.infrastructure.persistence.entity.DescuentoRecomendacionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SpringDataDescuentoRecomendacionRepository extends JpaRepository<DescuentoRecomendacionEntity, UUID> {

    List<DescuentoRecomendacionEntity> findByPacienteIdOrderByCreadoEnDesc(UUID pacienteId);

    boolean existsByReferidoId(UUID referidoId);
}
