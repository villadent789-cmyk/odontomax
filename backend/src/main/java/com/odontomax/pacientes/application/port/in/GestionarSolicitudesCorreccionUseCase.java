package com.odontomax.pacientes.application.port.in;

import com.odontomax.pacientes.application.dto.request.ResolverSolicitudCorreccionRequestDTO;
import com.odontomax.pacientes.application.dto.request.SolicitudCorreccionRequestDTO;
import com.odontomax.pacientes.application.dto.response.SolicitudCorreccionResponseDTO;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface GestionarSolicitudesCorreccionUseCase {
    SolicitudCorreccionResponseDTO solicitar(UUID pacienteId, SolicitudCorreccionRequestDTO dto);

    List<SolicitudCorreccionResponseDTO> listarPendientes();

    List<SolicitudCorreccionResponseDTO> listarDePaciente(UUID pacienteId);

    /**
     * Nombre, DNI y fecha de nacimiento solo los cambia el dueño: si quien edita no es administrador general y alguno de
     * esos datos cambia, lanza 403 indicando que debe solicitar la corrección. Un paciente inexistente no se valida aquí.
     */
    void exigirAutorizacionParaCambiarIdentidad(UUID pacienteId, String dni, String nombres, String apellidos, LocalDate fechaNacimiento);

    SolicitudCorreccionResponseDTO aprobar(UUID solicitudId, ResolverSolicitudCorreccionRequestDTO dto);

    SolicitudCorreccionResponseDTO rechazar(UUID solicitudId, ResolverSolicitudCorreccionRequestDTO dto);
}
