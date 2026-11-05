package com.odontomax.infrastructure.persistence.repository;

import com.odontomax.infrastructure.persistence.entity.CajaMovimientoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface SpringDataCajaMovimientoRepository extends JpaRepository<CajaMovimientoEntity, UUID> {

    List<CajaMovimientoEntity> findBySesionIdOrderByRegistradoEnAsc(UUID sesionId);

    @Query("SELECT COALESCE(SUM(m.monto), 0) FROM CajaMovimientoEntity m WHERE m.tipo = 'EGRESO' AND m.referenciaTipo = 'AJUSTE_MANUAL' AND m.registradoPor = :usuarioId AND m.sesion.sedeId = :sedeId AND m.registradoEn >= :desde")
    BigDecimal sumEgresosManualesUsuarioYDia(@Param("usuarioId") UUID usuarioId, @Param("sedeId") String sedeId, @Param("desde") Instant desde);

    @Query("SELECT COALESCE(SUM(m.monto), 0) FROM CajaMovimientoEntity m WHERE m.tipo = 'EGRESO' AND m.referenciaTipo = 'AJUSTE_MANUAL' AND m.sesion.sedeId = :sedeId AND m.registradoEn >= :desde")
    BigDecimal sumEgresosManualesSedeYDia(@Param("sedeId") String sedeId, @Param("desde") Instant desde);

    /** Egresos del rango agrupados por lo que los originó (gasto, laboratorio, comisión...). Cada fila: [referenciaTipo, suma]. */
    @Query("SELECT m.referenciaTipo, SUM(m.monto) FROM CajaMovimientoEntity m " +
            "WHERE m.tipo = 'EGRESO' AND m.sesion.sedeId = :sedeId " +
            "AND m.registradoEn >= :desde AND m.registradoEn < :hasta GROUP BY m.referenciaTipo")
    java.util.List<Object[]> sumEgresosPorCategoria(@Param("sedeId") String sedeId,
                                                    @Param("desde") Instant desde,
                                                    @Param("hasta") Instant hasta);

    @Query("SELECT COALESCE(SUM(m.monto), 0) FROM CajaMovimientoEntity m " +
            "WHERE m.tipo = 'EGRESO' AND m.sesion.sedeId = :sedeId " +
            "AND m.registradoEn >= :desde AND m.registradoEn < :hasta AND m.metodoPago = :metodo")
    BigDecimal sumEgresosSedeFechaYMetodo(@Param("sedeId") String sedeId,
                                          @Param("desde") Instant desde,
                                          @Param("hasta") Instant hasta,
                                          @Param("metodo") String metodo);
}
