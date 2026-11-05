package com.odontomax.sedes.infrastructure.persistence.repository;

import com.odontomax.sedes.infrastructure.persistence.entity.ServicioEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SpringDataServicioRepository extends JpaRepository<ServicioEntity, String> {
    List<ServicioEntity> findByActivoTrueAndUnidadDeNegocioOrderByOrdenAsc(String unidadDeNegocio);

    List<ServicioEntity> findByActivoTrueAndVisibleEnReservaTrueAndUnidadDeNegocioOrderByOrdenAsc(String unidadDeNegocio);

    List<ServicioEntity> findByUnidadDeNegocioOrderByOrdenAscNombreAsc(String unidadDeNegocio);

    java.util.Optional<ServicioEntity> findByNombreIgnoreCase(String nombre);
}
