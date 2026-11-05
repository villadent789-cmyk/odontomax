package com.odontomax.pacientes.application.service;

import com.odontomax.identidad.api.ClinicaUserPrincipal;
import com.odontomax.identidad.api.SecurityUtils;
import com.odontomax.pacientes.application.dto.response.HistoriaClinicaAccesoResponseDTO;
import com.odontomax.pacientes.application.port.in.GestionarAccesoHistoriaUseCase;
import com.odontomax.pacientes.application.port.out.AccesoHistoriaRepositoryPort;
import com.odontomax.pacientes.application.port.out.HistorialCitasPort;
import com.odontomax.pacientes.application.port.out.RegistroAuditoriaPort;
import com.odontomax.pacientes.domain.model.AccesoHistoria;
import com.odontomax.pacientes.infrastructure.persistence.entity.PacienteEntity;
import com.odontomax.pacientes.infrastructure.persistence.repository.SpringDataPacienteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccesoHistoriaApplicationService implements GestionarAccesoHistoriaUseCase {

    private final AccesoHistoriaRepositoryPort accesoRepository;
    private final SpringDataPacienteRepository pacienteRepository;
    private final RegistroAuditoriaPort registroAuditoria;
    private final HistorialCitasPort historialCitas;

    @Override
    @Transactional
    public HistoriaClinicaAccesoResponseDTO registrarApertura(UUID pacienteId) {
        historialCitas.exigirAccesoDeOdontologo(pacienteId);
        PacienteEntity paciente = pacienteRepository.findById(pacienteId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el paciente con ID: " + pacienteId));

        SecurityUtils.validarAccesoSede(paciente.getSedeId());

        ClinicaUserPrincipal user = SecurityUtils.getCurrentUser();
        String clientIp = SecurityUtils.getClientIp();

        if (user != null) {
            OffsetDateTime ventana = OffsetDateTime.now().minusMinutes(AccesoHistoria.VENTANA_DEDUPLICACION_MINUTOS);
            Optional<AccesoHistoria> accesoPropio = accesoRepository
                    .findFirstByPacienteIdAndUsuarioIdAndAccedidoEnAfterOrderByAccedidoEnDesc(pacienteId, user.id(), ventana);
            if (accesoPropio.isPresent()) {
                return toDto(accesoPropio.get());
            }
        }

        AccesoHistoria acceso = AccesoHistoria.registrar(pacienteId, user, paciente.getSedeId(), clientIp);
        AccesoHistoria guardado = accesoRepository.save(acceso);

        // Registrar también en auditoría general de accesos clínicos
        try {
            registroAuditoria.registrarAccesoHistoria(
                    guardado.getUsuarioId(),
                    guardado.getUsuarioNombre(),
                    guardado.getUsuarioRol(),
                    guardado.getSedeId(),
                    pacienteId,
                    "APERTURA_HISTORIA",
                    "Apertura de expediente e historia clínica del paciente " + paciente.getNombreCompleto(),
                    clientIp);
        } catch (Exception ignored) {
            // Un fallo en auditoría nunca impide registrar el acceso
        }

        return toDto(guardado);
    }

    @Override
    @Transactional
    public void registrarLecturaDeduplicada(UUID pacienteId, ClinicaUserPrincipal user, String ip) {
        if (user == null || pacienteId == null) return;
        OffsetDateTime ventana = OffsetDateTime.now().minusMinutes(AccesoHistoria.VENTANA_DEDUPLICACION_MINUTOS);
        if (accesoRepository.existsByPacienteIdAndUsuarioIdAndAccedidoEnAfter(pacienteId, user.id(), ventana)) {
            return;
        }

        PacienteEntity paciente = pacienteRepository.findById(pacienteId).orElse(null);
        if (paciente == null) return;

        AccesoHistoria acceso = AccesoHistoria.registrar(pacienteId, user, paciente.getSedeId(), ip != null ? ip : "127.0.0.1");
        accesoRepository.save(acceso);

        try {
            registroAuditoria.registrarAccesoHistoria(
                    user.id(),
                    user.nombre(),
                    user.rol(),
                    paciente.getSedeId(),
                    pacienteId,
                    "LECTURA_EXPEDIENTE",
                    "Lectura de expediente clínico del paciente " + paciente.getNombreCompleto(),
                    ip != null ? ip : "127.0.0.1");
        } catch (Exception ignored) {
            // Silencioso
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<HistoriaClinicaAccesoResponseDTO> listarAccesos(UUID pacienteId) {
        List<AccesoHistoria> lista;
        if (pacienteId != null) {
            lista = accesoRepository.findByPacienteIdOrderByAccedidoEnDesc(pacienteId);
        } else {
            lista = accesoRepository.findTop200ByOrderByAccedidoEnDesc();
        }
        return lista.stream()
                .map(AccesoHistoriaApplicationService::toDto)
                .toList();
    }

    private static HistoriaClinicaAccesoResponseDTO toDto(AccesoHistoria domain) {
        if (domain == null) return null;
        return HistoriaClinicaAccesoResponseDTO.builder()
                .id(domain.getId())
                .pacienteId(domain.getPacienteId())
                .usuarioId(domain.getUsuarioId())
                .usuarioNombre(domain.getUsuarioNombre())
                .usuarioEmail(domain.getUsuarioEmail())
                .usuarioRol(domain.getUsuarioRol())
                .sedeId(domain.getSedeId())
                .ip(domain.getIp())
                .accedidoEn(domain.getAccedidoEn())
                .build();
    }
}