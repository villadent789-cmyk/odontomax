package com.odontomax.agenda.infrastructure.persistence.repository;

import com.odontomax.agenda.infrastructure.persistence.entity.PacienteSaldoFavorEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SpringDataPacienteSaldoFavorRepository extends JpaRepository<PacienteSaldoFavorEntity, UUID> {
    List<PacienteSaldoFavorEntity> findByPacienteIdAndEstado(UUID pacienteId, String estado);
}
