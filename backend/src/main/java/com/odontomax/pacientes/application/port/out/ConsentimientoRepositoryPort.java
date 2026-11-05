package com.odontomax.pacientes.application.port.out;

import com.odontomax.pacientes.domain.model.Consentimiento;

import java.util.List;
import java.util.UUID;

public interface ConsentimientoRepositoryPort {
    List<Consentimiento> findByPacienteIdOrderByFirmadoEnDesc(UUID pacienteId);
    Consentimiento save(Consentimiento consentimiento);
    default java.util.Optional<Consentimiento> findById(UUID id) {
        return java.util.Optional.empty();
    }
}