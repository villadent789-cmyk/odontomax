package com.odontomax.domain.port.out;

import com.odontomax.domain.model.Radiografia;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RadiografiaRepositoryPort {
    Radiografia save(Radiografia radiografia);
    Optional<Radiografia> findById(UUID id);
    List<Radiografia> findByPacienteId(UUID pacienteId);
    List<Radiografia> findByPacienteIdAndTipo(UUID pacienteId, String tipoEstudio);
    void deleteById(UUID id);
}
