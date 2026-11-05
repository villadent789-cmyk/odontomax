package com.odontomax.pacientes.application.service;

import com.odontomax.identidad.api.ClinicaUserPrincipal;
import com.odontomax.identidad.api.SecurityUtils;
import com.odontomax.pacientes.application.dto.request.ResolverSolicitudCorreccionRequestDTO;
import com.odontomax.pacientes.application.dto.request.SolicitudEliminacionRequestDTO;
import com.odontomax.pacientes.application.dto.response.SolicitudEliminacionResponseDTO;
import com.odontomax.pacientes.application.port.in.EliminarPacienteUseCase;
import com.odontomax.pacientes.application.port.in.GestionarSolicitudesEliminacionUseCase;
import com.odontomax.pacientes.application.port.out.EliminacionObjetivosPort;
import com.odontomax.pacientes.application.port.out.EliminacionObjetivosPort.Objetivo;
import com.odontomax.pacientes.application.port.out.HistorialCitasPort;
import com.odontomax.pacientes.application.port.out.PacienteIdentidadPort;
import com.odontomax.pacientes.application.port.out.RegistroAuditoriaPort;
import com.odontomax.pacientes.application.port.out.SolicitudEliminacionRepositoryPort;
import com.odontomax.pacientes.domain.exception.PacienteNoEncontradoException;
import com.odontomax.pacientes.domain.exception.SolicitudCorreccionNoPendienteException;
import com.odontomax.pacientes.domain.model.SolicitudEliminacion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Eliminar una radiografía o un paciente con autorización del dueño: cualquier rol con acceso al paciente puede
 * solicitarlo; solo el administrador general lo aprueba (y entonces se elimina de verdad) o lo rechaza.
 */
@Service
@RequiredArgsConstructor
public class SolicitudEliminacionApplicationService implements GestionarSolicitudesEliminacionUseCase {

    private static final String TABLA = "solicitudes_eliminacion";

    private final SolicitudEliminacionRepositoryPort solicitudes;
    private final EliminacionObjetivosPort objetivos;
    private final PacienteIdentidadPort pacientes;
    private final HistorialCitasPort historialCitas;
    private final EliminarPacienteUseCase eliminarPaciente;
    private final RegistroAuditoriaPort auditoria;

    @Override
    @Transactional
    public SolicitudEliminacionResponseDTO solicitar(SolicitudEliminacionRequestDTO dto) {
        Objetivo objetivo = resolver(dto.getTipo(), dto.getObjetivoId());
        historialCitas.exigirAccesoDeOdontologo(objetivo.pacienteId());
        SecurityUtils.validarAccesoSede(objetivo.sedeId());
        if (solicitudes.existePendiente(dto.getTipo(), dto.getObjetivoId())) {
            throw new SolicitudCorreccionNoPendienteException("Ya hay una solicitud pendiente para eliminar esto. Espera la respuesta del dueño.");
        }
        ClinicaUserPrincipal usuario = SecurityUtils.getCurrentUser();

        SolicitudEliminacion nueva = SolicitudEliminacion.nueva(dto.getTipo(), dto.getObjetivoId(), objetivo.pacienteId(),
                objetivo.sedeId(), objetivo.descripcion(), dto.getMotivo(), usuario.id(), usuario.nombre());
        SolicitudEliminacion guardada = solicitudes.save(nueva);
        auditoria.registrarEventoClinico(usuario, objetivo.sedeId(), "SOLICITAR_ELIMINACION", TABLA, guardada.getId().toString(),
                "Eliminación solicitada: " + objetivo.descripcion() + " | Motivo: " + guardada.getMotivo(), SecurityUtils.getClientIp());
        return SolicitudEliminacionResponseDTO.from(guardada);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SolicitudEliminacionResponseDTO> listarPendientes() {
        return solicitudes.findByEstadoOrderBySolicitadoEnDesc(SolicitudEliminacion.PENDIENTE).stream()
                .map(SolicitudEliminacionResponseDTO::from).toList();
    }

    @Override
    @Transactional
    public SolicitudEliminacionResponseDTO aprobar(UUID solicitudId, ResolverSolicitudCorreccionRequestDTO dto) {
        SolicitudEliminacion s = obtener(solicitudId);
        ClinicaUserPrincipal usuario = SecurityUtils.getCurrentUser();
        s.aprobar(usuario.id(), usuario.nombre(), OffsetDateTime.now(), comentario(dto));

        // Si la eliminación no es posible (p. ej. el paciente ya tiene historial), esto lanza y todo se revierte:
        // la solicitud sigue pendiente y el dueño recibe la explicación.
        String motivo = "Solicitud de " + s.getSolicitadoPorNombre() + " aprobada por " + usuario.nombre() + ": " + s.getMotivo();
        if (SolicitudEliminacion.RADIOGRAFIA.equals(s.getTipo())) {
            objetivos.eliminarRadiografia(s.getObjetivoId(), motivo);
        } else {
            eliminarPaciente.eliminar(s.getObjetivoId(), usuario, SecurityUtils.getClientIp(), motivo);
        }

        SolicitudEliminacion guardada = solicitudes.save(s);
        auditoria.registrarEventoClinico(usuario, s.getSedeId(), "APROBAR_ELIMINACION", TABLA, solicitudId.toString(),
                "Eliminación aprobada por " + usuario.nombre() + ": " + s.getDescripcion(), SecurityUtils.getClientIp());
        return SolicitudEliminacionResponseDTO.from(guardada);
    }

    @Override
    @Transactional
    public SolicitudEliminacionResponseDTO rechazar(UUID solicitudId, ResolverSolicitudCorreccionRequestDTO dto) {
        SolicitudEliminacion s = obtener(solicitudId);
        ClinicaUserPrincipal usuario = SecurityUtils.getCurrentUser();
        s.rechazar(usuario.id(), usuario.nombre(), OffsetDateTime.now(), comentario(dto));
        SolicitudEliminacion guardada = solicitudes.save(s);
        auditoria.registrarEventoClinico(usuario, s.getSedeId(), "RECHAZAR_ELIMINACION", TABLA, solicitudId.toString(),
                "Eliminación rechazada por " + usuario.nombre() + ": " + s.getDescripcion(), SecurityUtils.getClientIp());
        return SolicitudEliminacionResponseDTO.from(guardada);
    }

    private Objetivo resolver(String tipo, UUID id) {
        if (SolicitudEliminacion.RADIOGRAFIA.equals(tipo)) {
            return objetivos.radiografia(id)
                    .orElseThrow(() -> new IllegalArgumentException("No se encontró la radiografía indicada."));
        }
        PacienteIdentidadPort.Identidad p = pacientes.obtener(id)
                .orElseThrow(() -> new PacienteNoEncontradoException("No se encontró el paciente con ID: " + id));
        return new Objetivo(p.id(), p.sedeId(), "Paciente " + p.nombres() + " " + p.apellidos() + " (DNI " + p.dni() + ")");
    }

    private SolicitudEliminacion obtener(UUID id) {
        return solicitudes.findById(id).orElseThrow(() -> new IllegalArgumentException("No se encontró la solicitud de eliminación."));
    }

    private static String comentario(ResolverSolicitudCorreccionRequestDTO dto) {
        return dto == null ? null : dto.getComentario();
    }
}
