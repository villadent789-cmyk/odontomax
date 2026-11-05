package com.odontomax.pacientes.infrastructure.adapter;

import com.odontomax.agenda.api.ConsultaCitasModuloApi;
import com.odontomax.agenda.api.ConsultaEspecialistasModuloApi;
import com.odontomax.application.dto.response.CitaResponseDTO;
import com.odontomax.pacientes.application.port.out.HistorialCitasPort;
import com.odontomax.pacientes.application.dto.response.PacienteResponseDTO;
import com.odontomax.pacientes.infrastructure.persistence.repository.SpringDataPacienteRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AgendaCitasAdapter implements HistorialCitasPort {

    private final ConsultaCitasModuloApi consultaCitasModuloApi;
    private final ConsultaEspecialistasModuloApi consultaEspecialistasModuloApi;
    private final SpringDataPacienteRepository pacienteRepository;

    @Override
    public Map<UUID, LocalDate> ultimasAtenciones(Collection<UUID> pacienteIds) {
        if (pacienteIds == null || pacienteIds.isEmpty()) return Map.of();
        return consultaCitasModuloApi.findUltimaAtencionPorPaciente(pacienteIds);
    }

    @Override
    public List<CitaResponseDTO> citasDePaciente(UUID pacienteId) {
        if (pacienteId == null) return List.of();
        return consultaCitasModuloApi.findByPacienteIdOrderByFechaDesc(pacienteId).stream().toList();
    }

    @Override
    public void exigirAccesoDeOdontologo(UUID pacienteId) {
        consultaEspecialistasModuloApi.exigirAccesoDeOdontologo(pacienteId);
    }
    @Override
    public List<PacienteResponseDTO> buscarPacientesOdontologo(String termino, String sedeId, UUID usuarioId) {
        return pacienteRepository.searchOdontologo(termino, sedeId, usuarioId)
                .stream()
                .map(PacienteResponseDTO::fromEntity)
                .toList();
    }

    @Override
    public Page<PacienteResponseDTO> buscarPacientesOdontologoPaginado(
            String termino, String sedeId, UUID usuarioId, Pageable pageable) {
        return pacienteRepository.searchPaginadoOdontologo(termino, sedeId, usuarioId, pageable)
                .map(PacienteResponseDTO::fromEntity);
    }
}