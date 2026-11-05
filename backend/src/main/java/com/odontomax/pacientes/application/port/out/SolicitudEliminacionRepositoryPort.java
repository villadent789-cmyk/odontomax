package com.odontomax.pacientes.application.port.out;

import com.odontomax.pacientes.domain.model.SolicitudEliminacion;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SolicitudEliminacionRepositoryPort {
    SolicitudEliminacion save(SolicitudEliminacion solicitud);

    Optional<SolicitudEliminacion> findById(UUID id);

    List<SolicitudEliminacion> findByEstadoOrderBySolicitadoEnDesc(String estado);

    boolean existePendiente(String tipo, UUID objetivoId);
}
