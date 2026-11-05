package com.odontomax.infrastructure.persistence.repository;

import com.odontomax.infrastructure.persistence.entity.AuditoriaLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SpringDataAuditoriaRepository extends JpaRepository<AuditoriaLogEntity, Long> {

    List<AuditoriaLogEntity> findTop200ByOrderByCreadoEnDesc();

    List<AuditoriaLogEntity> findTop200ByModuloIgnoreCaseOrderByCreadoEnDesc(String modulo);
}
