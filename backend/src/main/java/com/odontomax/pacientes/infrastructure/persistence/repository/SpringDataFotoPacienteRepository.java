package com.odontomax.pacientes.infrastructure.persistence.repository;

import com.odontomax.pacientes.infrastructure.persistence.entity.FotoPacienteEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SpringDataFotoPacienteRepository extends JpaRepository<FotoPacienteEntity, UUID> {
}
