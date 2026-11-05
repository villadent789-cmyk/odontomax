package com.odontomax.pacientes.infrastructure.adapter;

import com.odontomax.pacientes.api.VerificacionPacienteModuloApi;
import com.odontomax.pacientes.infrastructure.persistence.repository.SpringDataPacienteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VerificacionPacienteAdapter implements VerificacionPacienteModuloApi {

    private final SpringDataPacienteRepository pacienteRepository;

    @Override
    public boolean existePorDni(String dni) {
        if (dni == null || dni.isBlank()) {
            return false;
        }
        return pacienteRepository.findByDni(dni.trim()).isPresent();
    }
}
