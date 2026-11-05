package com.odontomax.identidad.infrastructure.persistence.repository;

import com.odontomax.identidad.infrastructure.persistence.entity.RecuperacionSolicitudEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface SpringDataRecuperacionSolicitudRepository extends JpaRepository<RecuperacionSolicitudEntity, Long> {

    @Query("SELECT COUNT(e) FROM RecuperacionSolicitudEntity e WHERE e.clave = :clave AND e.solicitadoEn > :desde")
    long countVigentesPorClave(@Param("clave") String clave, @Param("desde") long desde);

    @Modifying
    @Query("DELETE FROM RecuperacionSolicitudEntity e WHERE e.expiraEn < :ahora")
    int deleteCaducados(@Param("ahora") Instant ahora);
}
