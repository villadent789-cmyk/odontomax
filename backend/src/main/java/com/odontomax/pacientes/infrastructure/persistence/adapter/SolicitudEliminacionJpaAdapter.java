package com.odontomax.pacientes.infrastructure.persistence.adapter;

import com.odontomax.pacientes.application.port.out.SolicitudEliminacionRepositoryPort;
import com.odontomax.pacientes.domain.model.SolicitudEliminacion;
import com.odontomax.pacientes.infrastructure.persistence.entity.SolicitudEliminacionEntity;
import com.odontomax.pacientes.infrastructure.persistence.repository.SpringDataSolicitudEliminacionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SolicitudEliminacionJpaAdapter implements SolicitudEliminacionRepositoryPort {

    private final SpringDataSolicitudEliminacionRepository repository;

    @Override
    public SolicitudEliminacion save(SolicitudEliminacion s) {
        return toDomain(repository.save(toEntity(s)));
    }

    @Override
    public Optional<SolicitudEliminacion> findById(UUID id) {
        return repository.findById(id).map(SolicitudEliminacionJpaAdapter::toDomain);
    }

    @Override
    public List<SolicitudEliminacion> findByEstadoOrderBySolicitadoEnDesc(String estado) {
        return repository.findByEstadoOrderBySolicitadoEnDesc(estado).stream().map(SolicitudEliminacionJpaAdapter::toDomain).toList();
    }

    @Override
    public boolean existePendiente(String tipo, UUID objetivoId) {
        return repository.existsByTipoAndObjetivoIdAndEstado(tipo, objetivoId, SolicitudEliminacion.PENDIENTE);
    }

    private static SolicitudEliminacion toDomain(SolicitudEliminacionEntity e) {
        return SolicitudEliminacion.builder()
                .id(e.getId()).tipo(e.getTipo()).objetivoId(e.getObjetivoId()).pacienteId(e.getPacienteId())
                .sedeId(e.getSedeId()).descripcion(e.getDescripcion()).motivo(e.getMotivo()).estado(e.getEstado())
                .solicitadoPor(e.getSolicitadoPor()).solicitadoPorNombre(e.getSolicitadoPorNombre()).solicitadoEn(e.getSolicitadoEn())
                .resueltoPor(e.getResueltoPor()).resueltoPorNombre(e.getResueltoPorNombre()).resueltoEn(e.getResueltoEn())
                .comentarioResolucion(e.getComentarioResolucion())
                .build();
    }

    private static SolicitudEliminacionEntity toEntity(SolicitudEliminacion s) {
        SolicitudEliminacionEntity e = SolicitudEliminacionEntity.builder()
                .id(s.getId()).tipo(s.getTipo()).objetivoId(s.getObjetivoId()).pacienteId(s.getPacienteId())
                .sedeId(s.getSedeId()).descripcion(s.getDescripcion()).motivo(s.getMotivo()).estado(s.getEstado())
                .solicitadoPor(s.getSolicitadoPor()).solicitadoPorNombre(s.getSolicitadoPorNombre())
                .resueltoPor(s.getResueltoPor()).resueltoPorNombre(s.getResueltoPorNombre()).resueltoEn(s.getResueltoEn())
                .comentarioResolucion(s.getComentarioResolucion())
                .build();
        if (s.getSolicitadoEn() != null) {
            e.setSolicitadoEn(s.getSolicitadoEn());
        }
        return e;
    }
}
