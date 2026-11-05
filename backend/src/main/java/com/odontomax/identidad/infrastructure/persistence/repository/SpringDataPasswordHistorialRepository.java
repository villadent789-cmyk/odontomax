package com.odontomax.identidad.infrastructure.persistence.repository;

import com.odontomax.identidad.infrastructure.persistence.entity.PasswordHistorialEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SpringDataPasswordHistorialRepository extends JpaRepository<PasswordHistorialEntity, UUID> {

    List<PasswordHistorialEntity> findTop3ByUsuarioIdOrderByCreadoEnDesc(UUID usuarioId);
}
