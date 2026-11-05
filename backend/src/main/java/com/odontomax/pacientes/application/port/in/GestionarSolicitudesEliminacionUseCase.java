package com.odontomax.pacientes.application.port.in;

import com.odontomax.pacientes.application.dto.request.ResolverSolicitudCorreccionRequestDTO;
import com.odontomax.pacientes.application.dto.request.SolicitudEliminacionRequestDTO;
import com.odontomax.pacientes.application.dto.response.SolicitudEliminacionResponseDTO;

import java.util.List;
import java.util.UUID;

public interface GestionarSolicitudesEliminacionUseCase {
    SolicitudEliminacionResponseDTO solicitar(SolicitudEliminacionRequestDTO dto);

    List<SolicitudEliminacionResponseDTO> listarPendientes();

    /** Aprueba y elimina de verdad lo solicitado, con la misma regla que la eliminación directa del dueño. */
    SolicitudEliminacionResponseDTO aprobar(UUID solicitudId, ResolverSolicitudCorreccionRequestDTO dto);

    SolicitudEliminacionResponseDTO rechazar(UUID solicitudId, ResolverSolicitudCorreccionRequestDTO dto);
}
