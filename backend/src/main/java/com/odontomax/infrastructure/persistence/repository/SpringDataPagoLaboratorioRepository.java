package com.odontomax.infrastructure.persistence.repository;

import com.odontomax.infrastructure.persistence.entity.PagoLaboratorioEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SpringDataPagoLaboratorioRepository extends JpaRepository<PagoLaboratorioEntity, UUID> {
    List<PagoLaboratorioEntity> findByOrdenIdOrderByPagadoEnAsc(UUID ordenId);

    List<PagoLaboratorioEntity> findByOrdenIdInOrderByPagadoEnAsc(Collection<UUID> ordenIds);

    List<PagoLaboratorioEntity> findBySedeIdAndPagadoEnGreaterThanEqualAndPagadoEnLessThan(
            String sedeId, Instant desde, Instant hasta);

    List<PagoLaboratorioEntity> findByPagadoEnGreaterThanEqualAndPagadoEnLessThan(
            Instant desde, Instant hasta);
}
