package com.odontomax.infrastructure.persistence.repository;

import com.odontomax.infrastructure.persistence.entity.OrdenLaboratorioEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpringDataOrdenLaboratorioRepository extends JpaRepository<OrdenLaboratorioEntity, UUID> {

    Optional<OrdenLaboratorioEntity> findByCodigo(String codigo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM OrdenLaboratorioEntity o WHERE o.id = :id")
    Optional<OrdenLaboratorioEntity> findByIdForUpdate(@Param("id") UUID id);

    List<OrdenLaboratorioEntity> findAllByOrderByFechaSolicitudDescCreadoEnDesc();

    List<OrdenLaboratorioEntity> findBySedeIdOrderByFechaSolicitudDescCreadoEnDesc(String sedeId);

    List<OrdenLaboratorioEntity> findByPacienteIdOrderByFechaSolicitudDescCreadoEnDesc(UUID pacienteId);

    List<OrdenLaboratorioEntity> findByEspecialistaIdOrderByFechaSolicitudDescCreadoEnDesc(UUID especialistaId);

    @Query("""
        SELECT o FROM OrdenLaboratorioEntity o
        WHERE (:sedeId IS NULL OR o.sedeId = :sedeId)
          AND (:estado IS NULL OR o.estado = :estado)
          AND (:pacienteId IS NULL OR o.pacienteId = :pacienteId)
          AND (:especialistaId IS NULL OR o.especialistaId = :especialistaId)
        ORDER BY o.fechaSolicitud DESC, o.creadoEn DESC
    """)
    List<OrdenLaboratorioEntity> buscarOrdenes(
            @Param("sedeId") String sedeId,
            @Param("estado") String estado,
            @Param("pacienteId") UUID pacienteId,
            @Param("especialistaId") UUID especialistaId
    );

    @Query("""
        SELECT o FROM OrdenLaboratorioEntity o
        WHERE (:sedeId IS NULL OR o.sedeId = :sedeId)
          AND (:laboratorioId IS NULL OR o.laboratorioId = :laboratorioId)
          AND o.estado <> 'CANCELADO'
        ORDER BY o.laboratorioId, o.fechaSolicitud DESC, o.creadoEn DESC
    """)
    List<OrdenLaboratorioEntity> buscarOrdenesParaCuenta(
            @Param("sedeId") String sedeId,
            @Param("laboratorioId") UUID laboratorioId
    );

    /** Monto de laboratorio por doctor (precio al paciente o, si falta, el costo) de las órdenes no canceladas. */
    @Query("""
        SELECT o.especialistaId, COALESCE(SUM(COALESCE(o.precioPaciente, o.costoLaboratorio, 0)), 0)
        FROM OrdenLaboratorioEntity o
        WHERE o.estado <> 'CANCELADO'
          AND o.sedeId IN :sedes
          AND o.fechaSolicitud >= :desde AND o.fechaSolicitud <= :hasta
        GROUP BY o.especialistaId
    """)
    List<Object[]> montoPorDoctor(
            @Param("sedes") java.util.Collection<String> sedes,
            @Param("desde") java.time.LocalDate desde,
            @Param("hasta") java.time.LocalDate hasta
    );
}
