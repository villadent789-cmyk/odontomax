package com.odontomax.agenda.infrastructure.persistence.repository;

import com.odontomax.agenda.infrastructure.persistence.entity.EspecialistaHorarioEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SpringDataEspecialistaHorarioRepository extends JpaRepository<EspecialistaHorarioEntity, UUID> {

    List<EspecialistaHorarioEntity> findByEspecialistaIdOrderBySedeIdAscDiaSemanaAscHoraInicioAsc(UUID especialistaId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM especialista_horarios WHERE especialista_id = :especialistaId", nativeQuery = true)
    void eliminarPorEspecialista(@Param("especialistaId") UUID especialistaId);

    List<EspecialistaHorarioEntity> findByEspecialistaIdAndDiaSemanaAndActivoTrue(UUID especialistaId, Integer diaSemana);

    List<EspecialistaHorarioEntity> findByEspecialistaIdAndSedeIdAndDiaSemanaAndActivoTrue(UUID especialistaId, String sedeId, Integer diaSemana);

    List<EspecialistaHorarioEntity> findByEspecialistaIdAndActivoTrue(UUID especialistaId);
}
