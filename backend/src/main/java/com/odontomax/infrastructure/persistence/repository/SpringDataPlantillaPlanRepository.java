package com.odontomax.infrastructure.persistence.repository;

import com.odontomax.infrastructure.persistence.entity.PlantillaPlanEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SpringDataPlantillaPlanRepository extends JpaRepository<PlantillaPlanEntity, UUID> {

    List<PlantillaPlanEntity> findByUnidadDeNegocioAndActivoTrueOrderByNombreAsc(String unidadDeNegocio);

    boolean existsByUnidadDeNegocioAndActivoTrueAndNombreIgnoreCase(String unidadDeNegocio, String nombre);
}
