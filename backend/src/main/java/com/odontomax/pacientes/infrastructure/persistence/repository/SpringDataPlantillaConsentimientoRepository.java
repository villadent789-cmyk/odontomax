package com.odontomax.pacientes.infrastructure.persistence.repository;

import com.odontomax.pacientes.infrastructure.persistence.entity.PlantillaConsentimientoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SpringDataPlantillaConsentimientoRepository extends JpaRepository<PlantillaConsentimientoEntity, UUID> {

    List<PlantillaConsentimientoEntity> findByActivoTrueOrderByTituloAsc();

    Optional<PlantillaConsentimientoEntity> findByCodigo(String codigo);
}
