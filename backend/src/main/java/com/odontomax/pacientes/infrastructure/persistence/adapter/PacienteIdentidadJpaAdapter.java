package com.odontomax.pacientes.infrastructure.persistence.adapter;

import com.odontomax.pacientes.application.port.out.PacienteIdentidadPort;
import com.odontomax.pacientes.infrastructure.persistence.repository.SpringDataPacienteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PacienteIdentidadJpaAdapter implements PacienteIdentidadPort {

    private final SpringDataPacienteRepository pacientes;

    @Override
    public Optional<Identidad> obtener(UUID pacienteId) {
        return pacientes.findById(pacienteId)
                .map(p -> new Identidad(p.getId(), p.getSedeId(), p.getNombres(), p.getApellidos(), p.getDni(), p.getFechaNacimiento()));
    }

    @Override
    public void aplicar(UUID pacienteId, String nombres, String apellidos, String dni, LocalDate fechaNacimiento) {
        var paciente = pacientes.findById(pacienteId).orElseThrow(() -> new IllegalArgumentException("No se encontró el paciente."));
        paciente.setNombres(nombres);
        paciente.setApellidos(apellidos);
        paciente.setDni(dni);
        paciente.setFechaNacimiento(fechaNacimiento);
        pacientes.saveAndFlush(paciente);
    }
}
