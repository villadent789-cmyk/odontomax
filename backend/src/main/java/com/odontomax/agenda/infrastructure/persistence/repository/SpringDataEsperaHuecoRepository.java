package com.odontomax.agenda.infrastructure.persistence.repository;

import com.odontomax.agenda.infrastructure.persistence.entity.EsperaHuecoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface SpringDataEsperaHuecoRepository extends JpaRepository<EsperaHuecoEntity, UUID> {

    List<EsperaHuecoEntity> findBySedeIdAndEstadoInOrderByCreadoEnAsc(String sedeId, Collection<String> estados);

    boolean existsBySedeIdAndPacienteIdAndEstadoIn(String sedeId, UUID pacienteId, Collection<String> estados);
}
