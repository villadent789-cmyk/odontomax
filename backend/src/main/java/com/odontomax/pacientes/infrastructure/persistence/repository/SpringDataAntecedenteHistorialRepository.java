package com.odontomax.pacientes.infrastructure.persistence.repository;

import com.odontomax.pacientes.infrastructure.persistence.entity.AntecedenteHistorialEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SpringDataAntecedenteHistorialRepository extends JpaRepository<AntecedenteHistorialEntity, UUID> {

    List<AntecedenteHistorialEntity> findByPacienteIdOrderByCreadoEnDesc(UUID pacienteId);
}
