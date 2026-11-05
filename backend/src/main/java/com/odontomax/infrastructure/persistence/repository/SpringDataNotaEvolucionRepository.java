package com.odontomax.infrastructure.persistence.repository;

import com.odontomax.infrastructure.persistence.entity.NotaEvolucionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SpringDataNotaEvolucionRepository extends JpaRepository<NotaEvolucionEntity, UUID> {

    List<NotaEvolucionEntity> findByPacienteIdOrderByCreadoEnDesc(UUID pacienteId);

    List<NotaEvolucionEntity> findByCitaIdOrderByCreadoEnDesc(UUID citaId);

    List<NotaEvolucionEntity> findByNotaReferenciaId(UUID notaReferenciaId);
}
