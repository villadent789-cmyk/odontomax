package com.odontomax.sedes.infrastructure.persistence.repository;

import com.odontomax.sedes.infrastructure.persistence.entity.SedeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SpringDataSedeRepository extends JpaRepository<SedeEntity, String> {
    List<SedeEntity> findByActivaTrueOrderByNombreAsc();

    List<SedeEntity> findAllByOrderByNombreAsc();

    List<SedeEntity> findByUnidadDeNegocio(com.odontomax.sedes.model.UnidadDeNegocio unidadDeNegocio);
}
