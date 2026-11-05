package com.odontomax.infrastructure.persistence.repository;

import com.odontomax.infrastructure.persistence.entity.OrdenLaboratorioPruebaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SpringDataOrdenLaboratorioPruebaRepository extends JpaRepository<OrdenLaboratorioPruebaEntity, UUID> {

    List<OrdenLaboratorioPruebaEntity> findByOrdenIdOrderByNumeroAsc(UUID ordenId);

    long countByOrdenId(UUID ordenId);
}
