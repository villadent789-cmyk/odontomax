package com.odontomax.pacientes.infrastructure.persistence.repository;

import com.odontomax.pacientes.infrastructure.persistence.entity.SolicitudCorreccionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SpringDataSolicitudCorreccionRepository extends JpaRepository<SolicitudCorreccionEntity, UUID> {

    List<SolicitudCorreccionEntity> findByEstadoOrderBySolicitadoEnDesc(String estado);

    List<SolicitudCorreccionEntity> findByPacienteIdOrderBySolicitadoEnDesc(UUID pacienteId);
}
