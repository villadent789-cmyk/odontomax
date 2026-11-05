package com.odontomax.infrastructure.persistence.repository;

import com.odontomax.infrastructure.persistence.entity.ComisionRadiologicaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface SpringDataComisionRadiologicaRepository extends JpaRepository<ComisionRadiologicaEntity, UUID> {
    List<ComisionRadiologicaEntity> findAllByOrderByFechaEmisionDesc();
    List<ComisionRadiologicaEntity> findByEstadoOrderByFechaEmisionDesc(String estado);
    List<ComisionRadiologicaEntity> findByEstadoAndFechaLiquidacionBetween(String estado, java.time.OffsetDateTime desde, java.time.OffsetDateTime hasta);
    java.util.Optional<ComisionRadiologicaEntity> findByCitaId(UUID citaId);
    java.util.Optional<ComisionRadiologicaEntity> findByTratamientoId(UUID tratamientoId);
    List<ComisionRadiologicaEntity> findAllByTratamientoId(UUID tratamientoId);
    List<ComisionRadiologicaEntity> findAllByCitaId(UUID citaId);
    List<ComisionRadiologicaEntity> findByCitaIdIn(java.util.Collection<UUID> citaIds);
    List<ComisionRadiologicaEntity> findByDoctorDerivadorIdOrderByFechaEmisionDesc(UUID doctorDerivadorId);
    List<ComisionRadiologicaEntity> findByDoctorDerivadorIdAndEstadoOrderByFechaEmisionDesc(UUID doctorDerivadorId, String estado);

    @Query("SELECT COALESCE(SUM(c.montoComision), 0) FROM ComisionRadiologicaEntity c WHERE c.estado = 'PENDIENTE'")
    BigDecimal sumTotalComisionesPendientes();

    @Query("SELECT COALESCE(SUM(c.montoComision), 0) FROM ComisionRadiologicaEntity c WHERE c.doctorDerivadorId = :doctorId AND c.estado = 'PENDIENTE'")
    BigDecimal sumComisionesPendientesPorDoctor(@Param("doctorId") UUID doctorId);
}
