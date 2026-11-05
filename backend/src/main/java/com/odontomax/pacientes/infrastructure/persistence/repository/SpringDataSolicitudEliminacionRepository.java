package com.odontomax.pacientes.infrastructure.persistence.repository;

import com.odontomax.pacientes.infrastructure.persistence.entity.SolicitudEliminacionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SpringDataSolicitudEliminacionRepository extends JpaRepository<SolicitudEliminacionEntity, UUID> {

    List<SolicitudEliminacionEntity> findByEstadoOrderBySolicitadoEnDesc(String estado);

    boolean existsByTipoAndObjetivoIdAndEstado(String tipo, UUID objetivoId, String estado);
}
