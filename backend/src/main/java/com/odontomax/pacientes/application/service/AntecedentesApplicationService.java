package com.odontomax.pacientes.application.service;

import com.odontomax.identidad.api.ClinicaUserPrincipal;
import com.odontomax.identidad.api.SecurityUtils;
import com.odontomax.pacientes.application.dto.request.AntecedentesUpdateRequestDTO;
import com.odontomax.pacientes.application.dto.response.AntecedenteHistorialResponseDTO;
import com.odontomax.pacientes.application.dto.response.PacienteResponseDTO;
import com.odontomax.pacientes.application.port.in.GestionarAntecedentesUseCase;
import com.odontomax.pacientes.application.port.out.AntecedenteHistorialRepositoryPort;
import com.odontomax.pacientes.application.port.out.HistorialCitasPort;
import com.odontomax.pacientes.application.port.out.RegistroAuditoriaPort;
import com.odontomax.pacientes.domain.model.AntecedenteHistorial;
import com.odontomax.pacientes.infrastructure.persistence.entity.PacienteEntity;
import com.odontomax.pacientes.infrastructure.persistence.repository.SpringDataPacienteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AntecedentesApplicationService implements GestionarAntecedentesUseCase {

    private final SpringDataPacienteRepository pacienteRepository;
    private final AntecedenteHistorialRepositoryPort historialRepository;
    private final RegistroAuditoriaPort registroAuditoria;
    private final HistorialCitasPort historialCitas;

    @Override
    @Transactional(readOnly = true)
    public List<AntecedenteHistorialResponseDTO> obtenerHistorial(UUID pacienteId) {
        historialCitas.exigirAccesoDeOdontologo(pacienteId);
        PacienteEntity paciente = pacienteRepository.findById(pacienteId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el paciente con ID: " + pacienteId));

        SecurityUtils.validarAccesoSede(paciente.getSedeId());

        return historialRepository.findByPacienteIdOrderByCreadoEnDesc(pacienteId)
                .stream()
                .map(AntecedentesApplicationService::toHistorialDto)
                .toList();
    }

    @Override
    @Transactional
    public PacienteResponseDTO actualizarAntecedentes(UUID pacienteId, AntecedentesUpdateRequestDTO dto) {
        historialCitas.exigirAccesoDeOdontologo(pacienteId);
        PacienteEntity paciente = pacienteRepository.findById(pacienteId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el paciente con ID: " + pacienteId));

        SecurityUtils.validarAccesoSede(paciente.getSedeId());

        ClinicaUserPrincipal user = SecurityUtils.getCurrentUser();
        validarPermisoEscritura(user);

        paciente.actualizarAntecedentes(dto.getAlergias(), dto.getAntecedentesMedicos(), dto.getGestante(), dto.getSemanasGestacion());
        PacienteEntity actualizado = pacienteRepository.save(paciente);

        AntecedenteHistorial historial = AntecedenteHistorial.builder()
                .pacienteId(pacienteId)
                .alergias(dto.getAlergias())
                .antecedentesMedicos(dto.getAntecedentesMedicos())
                .gestante(dto.getGestante())
                .semanasGestacion(dto.getSemanasGestacion())
                .motivoCambio(dto.getMotivoCambio().trim())
                .modificadoPor(user != null ? user.id() : null)
                .modificadoPorNombre(user != null ? user.nombre() : "Sistema")
                .build();
        historialRepository.save(historial);

        registroAuditoria.registrarEventoClinico(
                user,
                paciente.getSedeId(),
                "ACTUALIZAR_ANTECEDENTES",
                "antecedentes_historial",
                pacienteId.toString(),
                "Actualización de alergias y antecedentes: " + dto.getMotivoCambio(),
                SecurityUtils.getClientIp());

        PacienteResponseDTO responseDTO = PacienteResponseDTO.fromEntity(actualizado);
        responseDTO.setAlergias(actualizado.alergiasConEspaciosNormalizados());
        return responseDTO;
    }

    private static AntecedenteHistorialResponseDTO toHistorialDto(AntecedenteHistorial domain) {
        if (domain == null) return null;
        return AntecedenteHistorialResponseDTO.builder()
                .id(domain.getId())
                .pacienteId(domain.getPacienteId())
                .alergias(domain.getAlergias())
                .antecedentesMedicos(domain.getAntecedentesMedicos())
                .gestante(domain.getGestante())
                .semanasGestacion(domain.getSemanasGestacion())
                .motivoCambio(domain.getMotivoCambio())
                .modificadoPor(domain.getModificadoPor())
                .modificadoPorNombre(domain.getModificadoPorNombre())
                .creadoEn(domain.getCreadoEn())
                .build();
    }

    private void validarPermisoEscritura(ClinicaUserPrincipal user) {
        if (user == null) {
            throw new AccessDeniedException("No autenticado.");
        }
        String rol = user.rol();
        if (!user.tieneAlgunRol("ODONTOLOGO", "ADMIN_SEDE", "ADMIN_GENERAL")) {
            throw new AccessDeniedException(
                    "El rol '" + rol + "' no tiene permisos para modificar antecedentes medicos ni alergias. Solo ODONTOLOGO, ADMIN_SEDE y ADMIN_GENERAL tienen acceso de escritura."
            );
        }
    }
}