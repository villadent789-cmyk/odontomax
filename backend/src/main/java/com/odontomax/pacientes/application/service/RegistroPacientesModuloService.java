package com.odontomax.pacientes.application.service;

import com.odontomax.pacientes.api.PacienteResumen;
import com.odontomax.pacientes.api.RegistroPacientesModuloApi;
import com.odontomax.pacientes.infrastructure.persistence.entity.PacienteEntity;
import com.odontomax.pacientes.infrastructure.persistence.repository.SpringDataPacienteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RegistroPacientesModuloService implements RegistroPacientesModuloApi {

    private final SpringDataPacienteRepository pacienteRepository;

    @Override
    public PacienteResumen obtenerOCrearPorDni(String dni, String nombres, String apellidos, String telefono, String sedeId) {
        PacienteEntity paciente = pacienteRepository.findByDni(dni.trim())
                .map(existente -> {
                    if (existente.getTelefono() == null || existente.getTelefono().isBlank()) {
                        existente.setTelefono(telefono.trim());
                        return pacienteRepository.save(existente);
                    }
                    return existente;
                })
                .orElseGet(() -> {
                    PacienteEntity nuevo = PacienteEntity.builder()
                            .id(UUID.randomUUID())
                            .dni(dni.trim())
                            .nombres(nombres.trim())
                            .apellidos(apellidos.trim())
                            .telefono(telefono.trim())
                            .sedeId(sedeId.trim())
                            .archivado(false)
                            .build();
                    return pacienteRepository.save(nuevo);
                });
        return new PacienteResumen(
                paciente.getId(), paciente.getDni(), paciente.getNombres(), paciente.getApellidos(),
                paciente.getTelefono(), paciente.getSedeId(), paciente.getFechaNacimiento(),
                paciente.getFuenteCaptacion(), paciente.getCreadoEn(), paciente.getTotalInasistencias());
    }
}
