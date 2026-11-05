package com.odontomax.infrastructure.persistence.repository;

import com.odontomax.infrastructure.persistence.entity.DoctorDerivadorEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SpringDataDoctorDerivadorRepository extends JpaRepository<DoctorDerivadorEntity, UUID> {
    List<DoctorDerivadorEntity> findByActivoTrueOrderByNombreAsc();
    List<DoctorDerivadorEntity> findAllByOrderByNombreAsc();
}
