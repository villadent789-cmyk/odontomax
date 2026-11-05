package com.odontomax.agenda.infrastructure.persistence.repository;

import com.odontomax.agenda.infrastructure.persistence.entity.CitaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SpringDataCitaRepository extends JpaRepository<CitaEntity, UUID> {

    List<CitaEntity> findByFechaAndSedeIdOrderByHoraAsc(LocalDate fecha, String sedeId);

    List<CitaEntity> findByFechaOrderByHoraAsc(LocalDate fecha);

    List<CitaEntity> findBySedeIdAndEstadoOrderByFechaAscHoraAsc(String sedeId, String estado);

    List<CitaEntity> findByEstadoOrderByFechaAscHoraAsc(String estado);

    // Mismos estados que liberan el hueco en las restricciones EXCLUDE de la base (V17).
    List<CitaEntity> findByTratamientoIdAndEstado(UUID tratamientoId, String estado);

    @Query("SELECT c FROM CitaEntity c WHERE c.doctorId = :doctorId " +
           "AND c.fecha BETWEEN :desde AND :hasta AND c.estado NOT IN ('CANCELADA', 'NO_ASISTIO', 'EXPIRADA')")
    List<CitaEntity> findOcupantesDoctor(@Param("doctorId") UUID doctorId,
                                         @Param("desde") LocalDate desde,
                                         @Param("hasta") LocalDate hasta);

    @Query("SELECT c FROM CitaEntity c WHERE c.sillonNumero = :sillonNumero AND c.sedeId = :sedeId " +
           "AND c.fecha BETWEEN :desde AND :hasta AND c.estado NOT IN ('CANCELADA', 'NO_ASISTIO', 'EXPIRADA')")
    List<CitaEntity> findOcupantesSillon(@Param("sedeId") String sedeId,
                                         @Param("sillonNumero") Integer sillonNumero,
                                         @Param("desde") LocalDate desde,
                                         @Param("hasta") LocalDate hasta);

    Optional<CitaEntity> findByCodigoTicket(String codigoTicket);

    @Query("SELECT COUNT(c) FROM CitaEntity c WHERE c.pacienteId = :pacienteId AND c.fecha >= :desde " +
           "AND c.estado IN ('PENDIENTE_WEB', 'PENDIENTE_DE_PAGO')")
    long countReservasWebActivasDelPaciente(@Param("pacienteId") UUID pacienteId, @Param("desde") LocalDate desde);

    @Query("SELECT COUNT(c) FROM CitaEntity c WHERE c.sedeId = :sedeId AND c.fecha = :fecha " +
           "AND c.estado = 'PENDIENTE_WEB' AND " +
           "((:isManana = true AND c.hora < :horaLimite) OR (:isManana = false AND c.hora >= :horaLimite))")
    long countPendientesWebPorTurno(@Param("sedeId") String sedeId,
                                    @Param("fecha") LocalDate fecha,
                                    @Param("isManana") boolean isManana,
                                    @Param("horaLimite") LocalTime horaLimite);

    @Query("SELECT c FROM CitaEntity c WHERE (:sedeId IS NULL OR c.sedeId = :sedeId) AND c.fecha BETWEEN :desde AND :hasta")
    List<CitaEntity> findByFechaBetweenAndSedeId(@Param("desde") LocalDate desde,
                                                 @Param("hasta") LocalDate hasta,
                                                 @Param("sedeId") String sedeId);

    List<CitaEntity> findByDoctorIdAndFechaAndEstadoNot(UUID doctorId, LocalDate fecha, String estado);

    List<CitaEntity> findByPacienteIdOrderByFechaDesc(UUID pacienteId);
    List<CitaEntity> findByTratamientoId(UUID tratamientoId);

    @Query("SELECT c.pacienteId, MAX(c.fecha) FROM CitaEntity c " +
           "WHERE c.estado = 'ATENDIDA' AND c.pacienteId IN :pacienteIds GROUP BY c.pacienteId")
    List<Object[]> findUltimaAtencionPorPaciente(@Param("pacienteIds") Collection<UUID> pacienteIds);

    @Query("SELECT c FROM CitaEntity c WHERE c.estado = 'PENDIENTE_DE_PAGO' AND (:sedeId IS NULL OR c.sedeId = :sedeId) ORDER BY c.adelantoLimitePago ASC")
    List<CitaEntity> findCitasPendientesDePago(@Param("sedeId") String sedeId);

    @Query("SELECT c FROM CitaEntity c WHERE c.estado = 'PENDIENTE_DE_PAGO' AND c.adelantoLimitePago < :ahora ORDER BY c.id")
    List<CitaEntity> findCitasVencidasDePago(@Param("ahora") OffsetDateTime ahora);
}
