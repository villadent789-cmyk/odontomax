package com.odontomax.clinica.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OdontogramaEpisodioRepository extends JpaRepository<OdontogramaEpisodioEntity, UUID> {

    List<OdontogramaEpisodioEntity> findByPacienteIdOrderByNumeroDesc(UUID pacienteId);

    Optional<OdontogramaEpisodioEntity> findByPacienteIdAndEstado(UUID pacienteId, String estado);

    int countByPacienteId(UUID pacienteId);
}
