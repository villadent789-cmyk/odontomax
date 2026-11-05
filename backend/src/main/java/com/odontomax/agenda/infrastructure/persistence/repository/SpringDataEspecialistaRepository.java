package com.odontomax.agenda.infrastructure.persistence.repository;

import com.odontomax.agenda.infrastructure.persistence.entity.EspecialistaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SpringDataEspecialistaRepository extends JpaRepository<EspecialistaEntity, UUID> {
    Optional<EspecialistaEntity> findByUsuarioId(UUID usuarioId);

    @Query("SELECT COUNT(c) > 0 FROM CitaEntity c, EspecialistaEntity e " +
           "WHERE e.id = c.doctorId AND e.usuarioId = :usuarioId AND c.pacienteId = :pacienteId")
    boolean existeCitaDelUsuarioConElPaciente(@Param("usuarioId") UUID usuarioId, @Param("pacienteId") UUID pacienteId);

    @Query("SELECT DISTINCT c.pacienteId FROM CitaEntity c, EspecialistaEntity e " +
           "WHERE e.id = c.doctorId AND e.usuarioId = :usuarioId")
    java.util.Set<UUID> findPacienteIdsByUsuarioId(@Param("usuarioId") UUID usuarioId);

    Optional<EspecialistaEntity> findFirstByEmailIgnoreCaseAndUsuarioIdIsNull(String email);

    List<EspecialistaEntity> findAllByOrderByNombreAsc();

    boolean existsByCop(String cop);

    boolean existsByCopAndIdNot(String cop, UUID id);

    @Modifying
    @Query(value = "INSERT INTO especialista_sedes (especialista_id, sede_id) VALUES (:especialistaId, :sedeId) ON CONFLICT DO NOTHING", nativeQuery = true)
    void vincularSede(@Param("especialistaId") UUID especialistaId, @Param("sedeId") String sedeId);

    @Modifying
    @Query(value = "DELETE FROM especialista_sedes WHERE especialista_id = :especialistaId AND sede_id = :sedeId", nativeQuery = true)
    void desvincularSede(@Param("especialistaId") UUID especialistaId, @Param("sedeId") String sedeId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM especialista_sedes WHERE especialista_id = :especialistaId", nativeQuery = true)
    void desvincularTodasLasSedes(@Param("especialistaId") UUID especialistaId);

    @Query(value = "SELECT (EXISTS (SELECT 1 FROM citas WHERE doctor_id = :id)"
            + " OR EXISTS (SELECT 1 FROM tratamientos WHERE especialista_id = :id)"
            + " OR EXISTS (SELECT 1 FROM lista_espera_huecos WHERE especialista_id = :id)"
            + " OR EXISTS (SELECT 1 FROM odontograma_registros WHERE especialista_id = :id)"
            + " OR EXISTS (SELECT 1 FROM radiografias_estudios WHERE doctor_id = :id)"
            + " OR EXISTS (SELECT 1 FROM ordenes_laboratorio WHERE especialista_id = :id)"
            + " OR EXISTS (SELECT 1 FROM pagos WHERE especialista_id = :id)"
            + " OR EXISTS (SELECT 1 FROM presupuestos WHERE especialista_id = :id))", nativeQuery = true)
    boolean tieneHistorialClinico(@Param("id") UUID id);

    List<EspecialistaEntity> findByActivoTrueOrderByNombreAsc();

    @Query(value = "SELECT DISTINCT e.* FROM especialistas e " +
            "INNER JOIN especialista_sedes es ON e.id = es.especialista_id " +
            "WHERE es.sede_id = :sedeId AND e.activo = true " +
            "ORDER BY e.nombre ASC", nativeQuery = true)
    List<EspecialistaEntity> findBySedeIdAndActivoTrue(@Param("sedeId") String sedeId);

    @Query(value = "SELECT es.sede_id FROM especialista_sedes es WHERE es.especialista_id = :especialistaId", nativeQuery = true)
    List<String> findSedeIdsByEspecialistaId(@Param("especialistaId") UUID especialistaId);

    @Query(value = "SELECT es.sede_id, es.sillon_numero FROM especialista_sedes es WHERE es.especialista_id = :especialistaId", nativeQuery = true)
    List<Object[]> findSillonesPorSede(@Param("especialistaId") UUID especialistaId);

    @Query(value = "SELECT es.especialista_id FROM especialista_sedes es "
            + "WHERE es.sede_id = :sedeId AND es.sillon_numero = :sillonNumero", nativeQuery = true)
    Optional<UUID> findEspecialistaIdBySedeYSillon(
            @Param("sedeId") String sedeId, @Param("sillonNumero") int sillonNumero);

    @Modifying
    @Query(value = "UPDATE especialista_sedes SET sillon_numero = :sillonNumero "
            + "WHERE especialista_id = :especialistaId AND sede_id = :sedeId", nativeQuery = true)
    void actualizarSillon(
            @Param("especialistaId") UUID especialistaId,
            @Param("sedeId") String sedeId,
            @Param("sillonNumero") Integer sillonNumero);
}
