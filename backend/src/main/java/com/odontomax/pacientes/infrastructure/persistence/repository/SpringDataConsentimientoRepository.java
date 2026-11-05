package com.odontomax.pacientes.infrastructure.persistence.repository;

import com.odontomax.pacientes.infrastructure.persistence.entity.ConsentimientoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SpringDataConsentimientoRepository extends JpaRepository<ConsentimientoEntity, UUID> {

    List<ConsentimientoEntity> findByPacienteIdOrderByFirmadoEnDesc(UUID pacienteId);

    List<ConsentimientoEntity> findByTratamientoId(UUID tratamientoId);
}
