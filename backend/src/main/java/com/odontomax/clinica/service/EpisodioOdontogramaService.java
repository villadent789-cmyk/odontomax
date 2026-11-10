package com.odontomax.clinica.service;

import com.odontomax.agenda.api.ConsultaEspecialistasModuloApi;
import com.odontomax.clinica.dto.AbrirEpisodioRequestDTO;
import com.odontomax.clinica.dto.CerrarEpisodioRequestDTO;
import com.odontomax.clinica.dto.EpisodioOdontogramaDTO;
import com.odontomax.clinica.dto.EpisodioOdontogramaDTO.PiezaInstantanea;
import com.odontomax.clinica.persistence.OdontogramaEpisodioEntity;
import com.odontomax.clinica.persistence.OdontogramaEpisodioRepository;
import com.odontomax.identidad.api.ClinicaUserPrincipal;
import com.odontomax.identidad.api.SecurityUtils;
import com.odontomax.infrastructure.persistence.entity.OdontogramaRegistroEntity;
import com.odontomax.infrastructure.persistence.repository.SpringDataOdontogramaRepository;
import com.odontomax.pacientes.api.ConsultaPacientesModuloApi;
import com.odontomax.pacientes.api.PacienteResumen;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Episodios del odontograma. Al abrir uno se guarda el odontograma INICIAL (cómo llega el paciente) y al cerrarlo, el
 * FINAL (cómo termina). Si el paciente vuelve meses después se abre otro episodio y se compara contra los anteriores.
 */
@Service
@RequiredArgsConstructor
public class EpisodioOdontogramaService {

    private static final Set<String> ROLES_ESCRITURA = Set.of("ODONTOLOGO", "ADMIN_SEDE", "ADMIN_GENERAL");

    private final OdontogramaEpisodioRepository episodioRepository;
    private final SpringDataOdontogramaRepository odontogramaRepository;
    private final ConsultaPacientesModuloApi pacientesApi;
    private final ConsultaEspecialistasModuloApi especialistasApi;

    @Transactional(readOnly = true)
    public List<EpisodioOdontogramaDTO> listar(UUID pacienteId) {
        exigirAcceso(pacienteId);
        return episodioRepository.findByPacienteIdOrderByNumeroDesc(pacienteId).stream()
                .map(EpisodioOdontogramaService::aDto)
                .toList();
    }

    @Transactional
    public EpisodioOdontogramaDTO abrir(UUID pacienteId, AbrirEpisodioRequestDTO req) {
        exigirAcceso(pacienteId);
        ClinicaUserPrincipal user = exigirEscritura();
        if (episodioRepository.findByPacienteIdAndEstado(pacienteId, "ABIERTO").isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El paciente ya tiene un episodio abierto. Ciérralo con el alta antes de abrir otro.");
        }
        OdontogramaEpisodioEntity episodio = OdontogramaEpisodioEntity.builder()
                .pacienteId(pacienteId)
                .numero(episodioRepository.countByPacienteId(pacienteId) + 1)
                .motivo(req != null && req.getMotivo() != null && !req.getMotivo().isBlank() ? req.getMotivo().trim() : null)
                .inicial(fotografiaActual(pacienteId))
                .inicialPor(user.nombre())
                .build();
        return aDto(episodioRepository.save(episodio));
    }

    @Transactional
    public EpisodioOdontogramaDTO cerrar(UUID pacienteId, UUID episodioId, CerrarEpisodioRequestDTO req) {
        exigirAcceso(pacienteId);
        ClinicaUserPrincipal user = exigirEscritura();
        OdontogramaEpisodioEntity episodio = episodioRepository.findById(episodioId)
                .filter(e -> e.getPacienteId().equals(pacienteId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Episodio no encontrado"));
        if (!"ABIERTO".equals(episodio.getEstado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este episodio ya está cerrado.");
        }
        episodio.setEstado("CERRADO");
        episodio.setFinalEn(OffsetDateTime.now());
        episodio.setFinalPiezas(fotografiaActual(pacienteId));
        episodio.setFinalPor(user.nombre());
        episodio.setNotaFinal(req != null && req.getNota() != null && !req.getNota().isBlank() ? req.getNota().trim() : null);
        return aDto(episodioRepository.save(episodio));
    }

    /** Estado actual de cada pieza con hallazgo o marcada como sana: lo que se ve en el odontograma ahora mismo. */
    private List<PiezaInstantanea> fotografiaActual(UUID pacienteId) {
        return odontogramaRepository.findEstadoActualPorPaciente(pacienteId).stream()
                .filter(r -> r.getEstado() != null)
                .sorted(Comparator.comparing(OdontogramaRegistroEntity::getPiezaFdi))
                .map(r -> PiezaInstantanea.builder()
                        .piezaFdi(r.getPiezaFdi())
                        .estado(r.getEstado().trim().toLowerCase())
                        .caras(r.getCaras() != null ? Arrays.asList(r.getCaras()) : List.of())
                        .observacion(r.getObservacion())
                        .build())
                .toList();
    }

    private PacienteResumen exigirAcceso(UUID pacienteId) {
        especialistasApi.exigirAccesoDeOdontologo(pacienteId);
        PacienteResumen paciente = pacientesApi.findById(pacienteId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el paciente con ID: " + pacienteId));
        SecurityUtils.validarAccesoSede(paciente.sedeId());
        return paciente;
    }

    private ClinicaUserPrincipal exigirEscritura() {
        ClinicaUserPrincipal user = SecurityUtils.getCurrentUser();
        if (user == null || user.roles().stream().noneMatch(ROLES_ESCRITURA::contains)) {
            throw new AccessDeniedException("Solo un odontólogo o un administrador abre y cierra episodios del odontograma.");
        }
        return user;
    }

    private static EpisodioOdontogramaDTO aDto(OdontogramaEpisodioEntity e) {
        return EpisodioOdontogramaDTO.builder()
                .id(e.getId())
                .pacienteId(e.getPacienteId())
                .numero(e.getNumero())
                .motivo(e.getMotivo())
                .estado(e.getEstado())
                .inicialEn(e.getInicialEn())
                .inicialPor(e.getInicialPor())
                .inicial(e.getInicial())
                .finalEn(e.getFinalEn())
                .finalPor(e.getFinalPor())
                .finalPiezas(e.getFinalPiezas())
                .notaFinal(e.getNotaFinal())
                .build();
    }
}
