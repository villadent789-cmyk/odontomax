package com.odontomax.pacientes.application.port.out;

import com.odontomax.pacientes.domain.model.AccesoHistoria;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccesoHistoriaRepositoryPort {
    Optional<AccesoHistoria> findFirstByPacienteIdAndUsuarioIdAndAccedidoEnAfterOrderByAccedidoEnDesc(
            UUID pacienteId, UUID usuarioId, OffsetDateTime timestamp);
    boolean existsByPacienteIdAndUsuarioIdAndAccedidoEnAfter(UUID pacienteId, UUID usuarioId, OffsetDateTime timestamp);
    AccesoHistoria save(AccesoHistoria acceso);
    List<AccesoHistoria> findByPacienteIdOrderByAccedidoEnDesc(UUID pacienteId);
    List<AccesoHistoria> findTop200ByOrderByAccedidoEnDesc();
}