package com.odontomax.infrastructure.persistence.repository;

import com.odontomax.infrastructure.persistence.entity.CajaSesionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SpringDataCajaSesionRepository extends JpaRepository<CajaSesionEntity, UUID> {

    Optional<CajaSesionEntity> findBySedeIdAndCerradaEnIsNull(String sedeId);

    Optional<CajaSesionEntity> findByAbiertaPorAndCerradaEnIsNull(UUID abiertaPor);

    Optional<CajaSesionEntity> findBySedeIdAndAbiertaPorAndCerradaEnIsNull(String sedeId, UUID abiertaPor);

    List<CajaSesionEntity> findBySedeIdOrderByAbiertaEnDesc(String sedeId);

    /** Cierres de la sede dentro de un rango, del más antiguo al más reciente. */
    List<CajaSesionEntity> findBySedeIdAndCerradaEnGreaterThanEqualAndCerradaEnLessThanOrderByCerradaEnAsc(
            String sedeId, java.time.Instant desde, java.time.Instant hasta);
}
