package com.odontomax.infrastructure.persistence.repository;

import com.odontomax.infrastructure.persistence.entity.GastoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface SpringDataGastoRepository extends JpaRepository<GastoEntity, UUID> {

    List<GastoEntity> findBySedeIdOrderByGastadoEnDesc(String sedeId);

    List<GastoEntity> findByCajaSesionIdOrderByGastadoEnAsc(UUID cajaSesionId);

    @Query("SELECT g FROM GastoEntity g WHERE g.sedeId = :sedeId AND g.gastadoEn BETWEEN :desde AND :hasta ORDER BY g.gastadoEn DESC")
    List<GastoEntity> findBySedeIdAndRangoFechas(@Param("sedeId") String sedeId, @Param("desde") Instant desde, @Param("hasta") Instant hasta);

    @Query("SELECT g FROM GastoEntity g WHERE g.gastadoEn BETWEEN :desde AND :hasta ORDER BY g.gastadoEn DESC")
    List<GastoEntity> findAllByRangoFechas(@Param("desde") Instant desde, @Param("hasta") Instant hasta);

    @Query("SELECT COALESCE(SUM(g.monto), 0) FROM GastoEntity g WHERE g.cajaSesionId = :sesionId AND g.metodoPago = 'EFECTIVO' AND g.anulado = false")
    BigDecimal sumEfectivoByCajaSesionId(@Param("sesionId") UUID sesionId);
}
