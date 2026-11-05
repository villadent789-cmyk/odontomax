package com.odontomax.agenda.infrastructure.persistence.repository;

import com.odontomax.agenda.infrastructure.persistence.entity.CitaContactoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SpringDataCitaContactoRepository extends JpaRepository<CitaContactoEntity, UUID> {
    List<CitaContactoEntity> findByCitaIdOrderByCreadoEnDesc(UUID citaId);
}
