package com.odontomax.pacientes.application.service;

import com.odontomax.identidad.api.ClinicaUserPrincipal;
import com.odontomax.identidad.api.SecurityUtils;
import com.odontomax.pacientes.application.dto.request.ResolverSolicitudCorreccionRequestDTO;
import com.odontomax.pacientes.application.dto.request.SolicitudCorreccionRequestDTO;
import com.odontomax.pacientes.application.dto.response.SolicitudCorreccionResponseDTO;
import com.odontomax.pacientes.application.port.in.GestionarSolicitudesCorreccionUseCase;
import com.odontomax.pacientes.application.port.out.HistorialCitasPort;
import com.odontomax.pacientes.application.port.out.PacienteIdentidadPort;
import com.odontomax.pacientes.application.port.out.PacienteIdentidadPort.Identidad;
import com.odontomax.pacientes.application.port.out.RegistroAuditoriaPort;
import com.odontomax.pacientes.application.port.out.SolicitudCorreccionRepositoryPort;
import com.odontomax.pacientes.domain.exception.PacienteNoEncontradoException;
import com.odontomax.pacientes.domain.model.SolicitudCorreccion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.List;
import java.util.UUID;

/**
 * Corrección de nombre, DNI y fecha de nacimiento con autorización del dueño: cualquier rol puede solicitarla; solo el
 * administrador general la aprueba (y entonces se aplica) o la rechaza.
 */
@Service
@RequiredArgsConstructor
public class SolicitudCorreccionApplicationService implements GestionarSolicitudesCorreccionUseCase {

    private final SolicitudCorreccionRepositoryPort solicitudes;
    private final PacienteIdentidadPort identidad;
    private final HistorialCitasPort historialCitas;
    private final RegistroAuditoriaPort auditoria;

    @Override
    @Transactional
    public SolicitudCorreccionResponseDTO solicitar(UUID pacienteId, SolicitudCorreccionRequestDTO dto) {
        historialCitas.exigirAccesoDeOdontologo(pacienteId);
        Identidad actual = obtenerIdentidad(pacienteId);
        SecurityUtils.validarAccesoSede(actual.sedeId());
        ClinicaUserPrincipal usuario = SecurityUtils.getCurrentUser();

        SolicitudCorreccion nueva = SolicitudCorreccion.nueva(pacienteId, actual.sedeId(), actual.nombres(), actual.apellidos(),
                actual.dni(), actual.fechaNacimiento(), dto.getNombres(), dto.getApellidos(), dto.getDni(),
                dto.getFechaNacimiento(), dto.getMotivo(), usuario.id(), usuario.nombre());
        SolicitudCorreccion guardada = solicitudes.save(nueva);
        auditoria.registrarEventoClinico(usuario, actual.sedeId(), "SOLICITAR_CORRECCION_PACIENTE", "solicitudes_correccion_paciente",
                guardada.getId().toString(), "Corrección solicitada para " + actual.nombres() + " " + actual.apellidos()
                        + " | Motivo: " + guardada.getMotivo(), SecurityUtils.getClientIp());
        return SolicitudCorreccionResponseDTO.from(guardada);
    }

    @Override
    @Transactional(readOnly = true)
    public void exigirAutorizacionParaCambiarIdentidad(UUID pacienteId, String dni, String nombres, String apellidos,
                                                       LocalDate fechaNacimiento) {
        ClinicaUserPrincipal usuario = SecurityUtils.getCurrentUser();
        if (usuario != null && "ADMIN_GENERAL".equals(usuario.rol())) {
            return;
        }
        identidad.obtener(pacienteId).ifPresent(actual -> {
            boolean cambia = !dni.trim().equals(actual.dni())
                    || !nombres.trim().equals(actual.nombres())
                    || !apellidos.trim().equals(actual.apellidos())
                    || !Objects.equals(fechaNacimiento, actual.fechaNacimiento());
            if (cambia) {
                throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN,
                        "Cambiar el nombre, el DNI o la fecha de nacimiento requiere la autorización del dueño. Usa «Solicitar corrección».");
            }
        });
    }

    @Override
    @Transactional(readOnly = true)
    public List<SolicitudCorreccionResponseDTO> listarPendientes() {
        return solicitudes.findByEstadoOrderBySolicitadoEnDesc(SolicitudCorreccion.PENDIENTE).stream()
                .map(SolicitudCorreccionResponseDTO::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SolicitudCorreccionResponseDTO> listarDePaciente(UUID pacienteId) {
        historialCitas.exigirAccesoDeOdontologo(pacienteId);
        SecurityUtils.validarAccesoSede(obtenerIdentidad(pacienteId).sedeId());
        return solicitudes.findByPacienteIdOrderBySolicitadoEnDesc(pacienteId).stream()
                .map(SolicitudCorreccionResponseDTO::from).toList();
    }

    @Override
    @Transactional
    public SolicitudCorreccionResponseDTO aprobar(UUID solicitudId, ResolverSolicitudCorreccionRequestDTO dto) {
        SolicitudCorreccion s = obtener(solicitudId);
        ClinicaUserPrincipal usuario = SecurityUtils.getCurrentUser();
        s.aprobar(usuario.id(), usuario.nombre(), OffsetDateTime.now(), comentario(dto));

        Identidad actual = obtenerIdentidad(s.getPacienteId());
        identidad.aplicar(s.getPacienteId(),
                s.getNombresNuevo() != null ? s.getNombresNuevo() : actual.nombres(),
                s.getApellidosNuevo() != null ? s.getApellidosNuevo() : actual.apellidos(),
                s.getDniNuevo() != null ? s.getDniNuevo() : actual.dni(),
                s.getFechaNacimientoNueva() != null ? s.getFechaNacimientoNueva() : actual.fechaNacimiento());

        SolicitudCorreccion guardada = solicitudes.save(s);
        auditoria.registrarEventoClinico(usuario, s.getSedeId(), "APROBAR_CORRECCION_PACIENTE", "solicitudes_correccion_paciente",
                solicitudId.toString(), "Corrección aprobada por " + usuario.nombre() + ": " + resumen(s), SecurityUtils.getClientIp());
        return SolicitudCorreccionResponseDTO.from(guardada);
    }

    @Override
    @Transactional
    public SolicitudCorreccionResponseDTO rechazar(UUID solicitudId, ResolverSolicitudCorreccionRequestDTO dto) {
        SolicitudCorreccion s = obtener(solicitudId);
        ClinicaUserPrincipal usuario = SecurityUtils.getCurrentUser();
        s.rechazar(usuario.id(), usuario.nombre(), OffsetDateTime.now(), comentario(dto));
        SolicitudCorreccion guardada = solicitudes.save(s);
        auditoria.registrarEventoClinico(usuario, s.getSedeId(), "RECHAZAR_CORRECCION_PACIENTE", "solicitudes_correccion_paciente",
                solicitudId.toString(), "Corrección rechazada por " + usuario.nombre() + ": " + resumen(s), SecurityUtils.getClientIp());
        return SolicitudCorreccionResponseDTO.from(guardada);
    }

    private Identidad obtenerIdentidad(UUID pacienteId) {
        return identidad.obtener(pacienteId).orElseThrow(() -> new PacienteNoEncontradoException("No se encontró el paciente con ID: " + pacienteId));
    }

    private SolicitudCorreccion obtener(UUID id) {
        return solicitudes.findById(id).orElseThrow(() -> new IllegalArgumentException("No se encontró la solicitud de corrección."));
    }

    private static String comentario(ResolverSolicitudCorreccionRequestDTO dto) {
        return dto == null ? null : dto.getComentario();
    }

    private static String resumen(SolicitudCorreccion s) {
        StringBuilder sb = new StringBuilder();
        agregar(sb, "nombres", s.getNombresAnterior(), s.getNombresNuevo());
        agregar(sb, "apellidos", s.getApellidosAnterior(), s.getApellidosNuevo());
        agregar(sb, "DNI", s.getDniAnterior(), s.getDniNuevo());
        agregar(sb, "fecha de nacimiento", s.getFechaNacimientoAnterior(), s.getFechaNacimientoNueva());
        return sb.toString();
    }

    private static void agregar(StringBuilder sb, String campo, Object antes, Object despues) {
        if (despues != null) {
            sb.append(sb.length() > 0 ? "; " : "").append(campo).append(" ").append(antes).append(" -> ").append(despues);
        }
    }
}
