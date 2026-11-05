package com.odontomax.pacientes.infrastructure.persistence.repository;

import com.odontomax.pacientes.infrastructure.persistence.entity.HistoriaClinicaAccesoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SpringDataHistoriaClinicaAccesoRepository extends JpaRepository<HistoriaClinicaAccesoEntity, UUID> {

    List<HistoriaClinicaAccesoEntity> findTop200ByOrderByAccedidoEnDesc();

    List<HistoriaClinicaAccesoEntity> findByPacienteIdOrderByAccedidoEnDesc(UUID pacienteId);

    boolean existsByPacienteIdAndUsuarioIdAndAccedidoEnAfter(UUID pacienteId, UUID usuarioId, java.time.OffsetDateTime timestamp);

    /** Último acceso de ESE usuario a la historia del paciente dentro de la ventana (deduplicación). */
    java.util.Optional<HistoriaClinicaAccesoEntity> findFirstByPacienteIdAndUsuarioIdAndAccedidoEnAfterOrderByAccedidoEnDesc(
            UUID pacienteId, UUID usuarioId, java.time.OffsetDateTime timestamp);
}
