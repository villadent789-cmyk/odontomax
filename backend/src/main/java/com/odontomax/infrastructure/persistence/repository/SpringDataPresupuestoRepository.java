package com.odontomax.infrastructure.persistence.repository;

import com.odontomax.infrastructure.persistence.entity.PresupuestoEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SpringDataPresupuestoRepository extends JpaRepository<PresupuestoEntity, UUID> {

    List<PresupuestoEntity> findByPacienteIdOrderByCreadoEnDesc(UUID pacienteId);

    List<PresupuestoEntity> findTop30BySedeIdAndEstadoOrderByCreadoEnDesc(String sedeId, String estado);
}
