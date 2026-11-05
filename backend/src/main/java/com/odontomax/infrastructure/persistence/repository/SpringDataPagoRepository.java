package com.odontomax.infrastructure.persistence.repository;

import com.odontomax.infrastructure.persistence.entity.PagoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface SpringDataPagoRepository extends JpaRepository<PagoEntity, UUID> {

    List<PagoEntity> findByPacienteIdOrderByPagadoEnDesc(UUID pacienteId);

    List<PagoEntity> findBySedeIdOrderByPagadoEnDesc(String sedeId);

    List<PagoEntity> findByTratamientoIdOrderByPagadoEnDesc(UUID tratamientoId);

    List<PagoEntity> findByCajaSesionIdOrderByPagadoEnAsc(UUID cajaSesionId);

    @Query("SELECT p FROM PagoEntity p WHERE p.sedeId = :sedeId AND p.pagadoEn BETWEEN :desde AND :hasta ORDER BY p.pagadoEn DESC")
    List<PagoEntity> findBySedeIdAndRangoFechas(@Param("sedeId") String sedeId, @Param("desde") Instant desde, @Param("hasta") Instant hasta);

    @Query("SELECT p FROM PagoEntity p WHERE p.pagadoEn BETWEEN :desde AND :hasta ORDER BY p.pagadoEn DESC")
    List<PagoEntity> findAllByRangoFechas(@Param("desde") Instant desde, @Param("hasta") Instant hasta);

    @Query("SELECT COALESCE(SUM(p.monto), 0) FROM PagoEntity p WHERE p.tratamientoId = :tratamientoId AND p.anulado = false")
    BigDecimal sumMontoPagadoByTratamientoId(@Param("tratamientoId") UUID tratamientoId);

    @Query("SELECT COALESCE(SUM(p.monto), 0) FROM PagoEntity p WHERE p.pacienteId = :pacienteId AND p.anulado = false")
    BigDecimal sumMontoPagadoByPacienteId(@Param("pacienteId") UUID pacienteId);

    @Query("SELECT COALESCE(SUM(p.monto), 0) FROM PagoEntity p WHERE p.cajaSesionId = :sesionId AND p.metodoPago = 'EFECTIVO' AND p.anulado = false")
    BigDecimal sumEfectivoByCajaSesionId(@Param("sesionId") UUID sesionId);

    @Query("SELECT COALESCE(SUM(p.monto), 0) FROM PagoEntity p WHERE p.cajaSesionId = :sesionId AND p.metodoPago = 'YAPE' AND p.anulado = false")
    BigDecimal sumYapeByCajaSesionId(@Param("sesionId") UUID sesionId);

    @Query(value = """
            SELECT COALESCE(SUM(p.monto), 0)
            FROM pagos p
            WHERE p.caja_sesion_id = :sesionId
              AND p.anulado = false
              AND p.metodo_pago = :metodo
              AND NOT EXISTS (
                  SELECT 1 FROM caja_movimientos m
                  WHERE m.referencia_tipo = 'PAGO_TRATAMIENTO'
                    AND m.referencia_id = p.id
                    AND m.tipo = 'INGRESO'
              )
            """, nativeQuery = true)
    BigDecimal sumPagosSinMovimientoPorSesionYMetodo(
            @Param("sesionId") UUID sesionId, @Param("metodo") String metodo);

    @Query("SELECT COALESCE(SUM(p.monto), 0) FROM PagoEntity p " +
            "WHERE p.sedeId = :sedeId AND p.pagadoEn >= :desde AND p.pagadoEn < :hasta " +
            "AND p.anulado = false AND p.metodoPago = :metodo")
    BigDecimal sumPorSedeFechaYMetodo(@Param("sedeId") String sedeId,
                                      @Param("desde") Instant desde,
                                      @Param("hasta") Instant hasta,
                                      @Param("metodo") String metodo);
}
