package com.odontomax.agenda.application.service;

import com.odontomax.agenda.application.dto.request.EsperaHuecoRequestDTO;
import com.odontomax.agenda.application.dto.response.EsperaHuecoResponseDTO;
import com.odontomax.agenda.application.port.in.GestionarListaEsperaUseCase;
import com.odontomax.agenda.application.port.out.EspecialistaRepositoryPort;
import com.odontomax.agenda.application.port.out.EsperaHuecoRepositoryPort;
import com.odontomax.agenda.application.port.out.PacienteReservaPort;
import com.odontomax.agenda.domain.model.EsperaHueco;
import com.odontomax.identidad.api.ClinicaUserPrincipal;
import com.odontomax.identidad.api.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Lista de espera de huecos: pacientes sin cita que esperan que se libere un horario. */
@Service
@RequiredArgsConstructor
public class ListaEsperaApplicationService implements GestionarListaEsperaUseCase {

    private final EsperaHuecoRepositoryPort esperaRepository;
    private final PacienteReservaPort pacienteReservaPort;
    private final EspecialistaRepositoryPort especialistaRepository;

    @Override
    @Transactional
    public EsperaHuecoResponseDTO agregar(EsperaHuecoRequestDTO request) {
        String sedeId = request.getSedeId().trim();
        SecurityUtils.validarAccesoSede(sedeId);
        var paciente = pacienteReservaPort.findById(request.getPacienteId())
                .orElseThrow(() -> new IllegalArgumentException("Paciente no encontrado con ID: " + request.getPacienteId()));
        if (request.getEspecialistaId() != null && especialistaRepository.findBySedeIdAndActivoTrue(sedeId).stream()
                .noneMatch(e -> e.getId().equals(request.getEspecialistaId()))) {
            throw new IllegalArgumentException("El doctor elegido no atiende en esta sede.");
        }
        if (esperaRepository.existeVigenteDelPaciente(sedeId, request.getPacienteId())) {
            throw new IllegalArgumentException("El paciente ya está en la lista de espera de esta sede.");
        }
        ClinicaUserPrincipal user = SecurityUtils.getCurrentUser();
        EsperaHueco guardada = esperaRepository.save(new EsperaHueco(null, sedeId, paciente.id(), paciente.nombreCompleto(),
                paciente.telefono(), request.getEspecialistaId(), request.getMotivo(), request.getNotas(), EsperaHueco.ACTIVA,
                user != null ? user.id() : null, Instant.now()));
        return EsperaHuecoResponseDTO.fromDomain(guardada);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EsperaHuecoResponseDTO> listar(String sedeId) {
        String sedeEfectiva = SecurityUtils.resolverSedeEfectiva(sedeId);
        if (sedeEfectiva == null) {
            throw new IllegalArgumentException("Elige una sede para ver su lista de espera.");
        }
        return esperaRepository.findVigentesBySede(sedeEfectiva).stream().map(EsperaHuecoResponseDTO::fromDomain).toList();
    }

    @Override
    @Transactional
    public EsperaHuecoResponseDTO cambiarEstado(UUID id, String estado) {
        EsperaHueco espera = esperaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Espera no encontrada con ID: " + id));
        SecurityUtils.validarAccesoSede(espera.getSedeId());
        espera.cambiarEstado(estado == null ? "" : estado.trim().toUpperCase());
        return EsperaHuecoResponseDTO.fromDomain(esperaRepository.save(espera));
    }
}
