package com.odontomax.pacientes.application.port.out;

import com.odontomax.pacientes.domain.model.SolicitudCorreccion;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SolicitudCorreccionRepositoryPort {
    SolicitudCorreccion save(SolicitudCorreccion solicitud);

    Optional<SolicitudCorreccion> findById(UUID id);

    List<SolicitudCorreccion> findByEstadoOrderBySolicitadoEnDesc(String estado);

    List<SolicitudCorreccion> findByPacienteIdOrderBySolicitadoEnDesc(UUID pacienteId);
}
