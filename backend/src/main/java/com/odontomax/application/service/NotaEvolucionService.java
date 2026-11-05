package com.odontomax.application.service;

import com.odontomax.application.dto.request.NotaEvolucionRequestDTO;
import com.odontomax.application.dto.response.NotaEvolucionResponseDTO;
import com.odontomax.infrastructure.persistence.entity.AuditoriaLogEntity;
import com.odontomax.infrastructure.persistence.entity.NotaEvolucionEntity;
import com.odontomax.pacientes.infrastructure.persistence.entity.PacienteEntity;
import com.odontomax.infrastructure.persistence.repository.SpringDataAuditoriaRepository;
import com.odontomax.infrastructure.persistence.repository.SpringDataNotaEvolucionRepository;
import com.odontomax.pacientes.infrastructure.persistence.repository.SpringDataPacienteRepository;
import com.odontomax.agenda.api.ConsultaEspecialistasModuloApi;
import com.odontomax.identidad.api.ClinicaUserPrincipal;
import com.odontomax.identidad.api.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotaEvolucionService {

    private final SpringDataNotaEvolucionRepository notaRepository;
    private final SpringDataPacienteRepository pacienteRepository;
    private final SpringDataAuditoriaRepository auditoriaRepository;
    private final ConsultaEspecialistasModuloApi consultaEspecialistasApi;

    @Transactional(readOnly = true)
    public List<NotaEvolucionResponseDTO> listarPorPaciente(UUID pacienteId) {
        consultaEspecialistasApi.exigirAccesoDeOdontologo(pacienteId);
        PacienteEntity paciente = pacienteRepository.findById(pacienteId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el paciente con ID: " + pacienteId));

        SecurityUtils.validarAccesoSede(paciente.getSedeId());

        return notaRepository.findByPacienteIdOrderByCreadoEnDesc(pacienteId)
                .stream()
                .map(NotaEvolucionResponseDTO::fromEntity)
                .toList();
    }

    @Transactional
    public NotaEvolucionResponseDTO crearNota(UUID pacienteId, NotaEvolucionRequestDTO dto) {
        consultaEspecialistasApi.exigirAccesoDeOdontologo(pacienteId);
        PacienteEntity paciente = pacienteRepository.findById(pacienteId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el paciente con ID: " + pacienteId));

        SecurityUtils.validarAccesoSede(paciente.getSedeId());

        ClinicaUserPrincipal user = SecurityUtils.getCurrentUser();
        validarPermisoEscritura(user);

        if (dto.getNotaReferenciaId() != null) {
            NotaEvolucionEntity notaPrevia = notaRepository.findById(dto.getNotaReferenciaId())
                    .orElseThrow(() -> new IllegalArgumentException("No se encontró la nota clínica de referencia con ID: " + dto.getNotaReferenciaId()));
            if (!notaPrevia.getPacienteId().equals(pacienteId)) {
                throw new IllegalArgumentException("La nota clínica previa no corresponde al paciente especificado.");
            }
        }

        NotaEvolucionEntity entity = NotaEvolucionEntity.builder()
                .pacienteId(pacienteId)
                .citaId(dto.getCitaId())
                .autorId(user.id())
                .autorNombre(user.nombre())
                .autorRol(user.rol())
                .tipoNota(dto.getTipoNota() != null && !dto.getTipoNota().isBlank() ? dto.getTipoNota().toUpperCase() : "SOAP")
                .subjetivo(dto.getSubjetivo())
                .objetivo(dto.getObjetivo())
                .analisis(dto.getAnalisis())
                .plan(dto.getPlan())
                .contenido(dto.getContenido())
                .notaReferenciaId(dto.getNotaReferenciaId())
                .motivoCorreccion(dto.getMotivoCorreccion())
                .firmado(true) // Inmutable al crearse
                .build();

        NotaEvolucionEntity guardada = notaRepository.save(entity);

        registrarAuditoriaEscritura(user, paciente, "CREAR_NOTA_EVOLUCION",
                String.format("Nota de evolución creada para paciente %s (%s)", paciente.getId(), entity.getTipoNota()));

        return NotaEvolucionResponseDTO.fromEntity(guardada);
    }

    private void validarPermisoEscritura(ClinicaUserPrincipal user) {
        if (user == null) {
            throw new AccessDeniedException("No autenticado.");
        }
        String rol = user.rol();
        if (!"ODONTOLOGO".equalsIgnoreCase(rol) &&
            !"ADMIN_SEDE".equalsIgnoreCase(rol) &&
            !"ADMIN_GENERAL".equalsIgnoreCase(rol)) {
            throw new AccessDeniedException(
                    "El rol '" + rol + "' no tiene permisos para redactar notas clínicas de evolución. Solo ODONTOLOGO, ADMIN_SEDE y ADMIN_GENERAL tienen acceso de escritura."
            );
        }
    }

    private void registrarAuditoriaEscritura(ClinicaUserPrincipal user, PacienteEntity paciente, String accion, String detalle) {
        try {
            auditoriaRepository.save(AuditoriaLogEntity.builder()
                    .usuarioId(user != null ? user.id() : null)
                    .usuarioNombre(user != null ? user.nombre() : "Sistema")
                    .rol(user != null ? user.rol() : "ANONYMOUS")
                    .sedeId(paciente.getSedeId())
                    .modulo("HISTORIA_CLINICA")
                    .accion(accion)
                    .tabla("notas_evolucion")
                    .registroId(paciente.getId().toString())
                    .detalle(detalle)
                    .ip(SecurityUtils.getClientIp())
                    .build());
        } catch (Exception ignored) {
        }
    }
}
