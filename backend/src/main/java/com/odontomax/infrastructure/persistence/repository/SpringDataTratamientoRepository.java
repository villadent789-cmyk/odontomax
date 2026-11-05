package com.odontomax.infrastructure.persistence.repository;

import com.odontomax.infrastructure.persistence.entity.TratamientoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface SpringDataTratamientoRepository extends JpaRepository<TratamientoEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TratamientoEntity t WHERE t.id = :id")
    java.util.Optional<TratamientoEntity> findByIdForUpdate(@Param("id") UUID id);

    List<TratamientoEntity> findByPacienteIdOrderByCreadoEnDesc(UUID pacienteId);

    List<TratamientoEntity> findBySedeIdOrderByCreadoEnDesc(String sedeId);

    List<TratamientoEntity> findBySedeIdAndEstado(String sedeId, String estado);

    @Query("SELECT COUNT(t) FROM TratamientoEntity t WHERE t.sedeId = :sedeId AND t.creadoEn BETWEEN :desde AND :hasta")
    long countBySedeIdAndCreadoEnBetween(@Param("sedeId") String sedeId, @Param("desde") Instant desde, @Param("hasta") Instant hasta);

    @Query("SELECT COALESCE(SUM(t.costoTotal), 0) FROM TratamientoEntity t WHERE t.pacienteId = :pacienteId AND t.estado <> 'CANCELADO'")
    BigDecimal sumCostoTotalByPacienteId(@Param("pacienteId") UUID pacienteId);
}
