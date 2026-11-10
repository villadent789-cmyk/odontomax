package com.odontomax.application.service;

import com.odontomax.application.dto.request.OdontogramaLoteRequestDTO;
import com.odontomax.application.dto.request.OdontogramaPiezaRequestDTO;
import com.odontomax.application.dto.response.OdontogramaRegistroResponseDTO;
import com.odontomax.infrastructure.persistence.entity.AuditoriaLogEntity;
import com.odontomax.infrastructure.persistence.entity.OdontogramaRegistroEntity;
import com.odontomax.pacientes.infrastructure.persistence.entity.PacienteEntity;
import com.odontomax.infrastructure.persistence.repository.SpringDataAuditoriaRepository;
import com.odontomax.infrastructure.persistence.repository.SpringDataOdontogramaRepository;
import com.odontomax.pacientes.infrastructure.persistence.repository.SpringDataPacienteRepository;
import com.odontomax.agenda.api.ConsultaEspecialistasModuloApi;
import com.odontomax.identidad.api.ClinicaUserPrincipal;
import com.odontomax.identidad.api.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OdontogramaService {

    private final SpringDataOdontogramaRepository odontogramaRepository;
    private final SpringDataPacienteRepository pacienteRepository;
    private final SpringDataAuditoriaRepository auditoriaRepository;
    private final ConsultaEspecialistasModuloApi consultaEspecialistasApi;

    @Transactional(readOnly = true)
    public List<OdontogramaRegistroResponseDTO> obtenerEstadoActual(UUID pacienteId) {
        consultaEspecialistasApi.exigirAccesoDeOdontologo(pacienteId);
        PacienteEntity paciente = pacienteRepository.findById(pacienteId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el paciente con ID: " + pacienteId));
        SecurityUtils.validarAccesoSede(paciente.getSedeId());

        return odontogramaRepository.findEstadoActualPorPaciente(pacienteId)
                .stream()
                .map(OdontogramaRegistroResponseDTO::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OdontogramaRegistroResponseDTO> obtenerHistorial(UUID pacienteId) {
        consultaEspecialistasApi.exigirAccesoDeOdontologo(pacienteId);
        PacienteEntity paciente = pacienteRepository.findById(pacienteId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el paciente con ID: " + pacienteId));
        SecurityUtils.validarAccesoSede(paciente.getSedeId());

        return odontogramaRepository.findByPacienteIdOrderByRegistradoEnDesc(pacienteId)
                .stream()
                .map(OdontogramaRegistroResponseDTO::fromEntity)
                .toList();
    }

    @Transactional
    public OdontogramaRegistroResponseDTO registrarPieza(UUID pacienteId, OdontogramaPiezaRequestDTO dto) {
        consultaEspecialistasApi.exigirAccesoDeOdontologo(pacienteId);
        PacienteEntity paciente = pacienteRepository.findById(pacienteId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el paciente con ID: " + pacienteId));
        SecurityUtils.validarAccesoSede(paciente.getSedeId());

        ClinicaUserPrincipal user = SecurityUtils.getCurrentUser();
        validarPermisoEscritura(user);

        String[] carasArray = dto.getCaras() != null ? dto.getCaras().toArray(new String[0]) : new String[0];

        OdontogramaRegistroEntity entity = OdontogramaRegistroEntity.builder()
                .pacienteId(pacienteId)
                .piezaFdi(dto.getPiezaFdi())
                .estado(dto.getEstado().trim().toLowerCase())
                .caras(carasArray)
                .observacion(dto.getObservacion())
                .activo(dto.getActivo() == null || dto.getActivo())
                .especialistaId(dto.getEspecialistaId())
                .registradoPor(user != null ? user.id() : null)
                .build();

        OdontogramaRegistroEntity guardado = odontogramaRepository.save(entity);

        registrarAuditoriaEscritura(user, paciente, "ACTUALIZAR_PIEZA",
                String.format("Pieza %d registrada con estado '%s'", dto.getPiezaFdi(), dto.getEstado()));

        return OdontogramaRegistroResponseDTO.fromEntity(guardado);
    }

    @Transactional
    public List<OdontogramaRegistroResponseDTO> registrarLote(UUID pacienteId, OdontogramaLoteRequestDTO dto) {
        consultaEspecialistasApi.exigirAccesoDeOdontologo(pacienteId);
        PacienteEntity paciente = pacienteRepository.findById(pacienteId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el paciente con ID: " + pacienteId));
        SecurityUtils.validarAccesoSede(paciente.getSedeId());

        ClinicaUserPrincipal user = SecurityUtils.getCurrentUser();
        validarPermisoEscritura(user);

        List<OdontogramaRegistroResponseDTO> guardados = new ArrayList<>();

        for (OdontogramaPiezaRequestDTO item : dto.getPiezas()) {
            String[] carasArray = item.getCaras() != null ? item.getCaras().toArray(new String[0]) : new String[0];
            OdontogramaRegistroEntity entity = OdontogramaRegistroEntity.builder()
                    .pacienteId(pacienteId)
                    .piezaFdi(item.getPiezaFdi())
                    .estado(item.getEstado().trim().toLowerCase())
                    .caras(carasArray)
                    .observacion(item.getObservacion())
                    .activo(item.getActivo() == null || item.getActivo())
                    .especialistaId(item.getEspecialistaId())
                    .registradoPor(user != null ? user.id() : null)
                    .build();

            OdontogramaRegistroEntity saved = odontogramaRepository.save(entity);
            guardados.add(OdontogramaRegistroResponseDTO.fromEntity(saved));
        }

        registrarAuditoriaEscritura(user, paciente, "ACTUALIZAR_LOTE_ODONTOGRAMA",
                String.format("Lote de %d pieza(s) dentales registrado", dto.getPiezas().size()));

        return guardados;
    }

    private void validarPermisoEscritura(ClinicaUserPrincipal user) {
        if (user == null) {
            throw new AccessDeniedException("No autenticado.");
        }
        String rol = user.rol();
        if (!user.tieneAlgunRol("ODONTOLOGO", "RECEPCION", "ADMIN_SEDE", "ADMIN_GENERAL")) {
            throw new AccessDeniedException(
                    "El rol '" + rol + "' no tiene permisos para modificar o asentar registros en el odontograma. Solo ODONTOLOGO, ADMIN_SEDE y ADMIN_GENERAL tienen acceso de escritura."
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
                    .modulo("ODONTOGRAMA")
                    .accion(accion)
                    .tabla("odontograma_registros")
                    .registroId(paciente.getId().toString())
                    .detalle(detalle)
                    .ip(SecurityUtils.getClientIp())
                    .build());
        } catch (Exception ignored) {
        }
    }
}
