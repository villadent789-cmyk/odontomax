package com.odontomax.pacientes.infrastructure.persistence.adapter;

import com.odontomax.pacientes.infrastructure.persistence.entity.HistoriaClinicaAccesoEntity;
import com.odontomax.pacientes.infrastructure.persistence.repository.SpringDataHistoriaClinicaAccesoRepository;
import com.odontomax.pacientes.application.port.out.AccesoHistoriaRepositoryPort;
import com.odontomax.pacientes.domain.model.AccesoHistoria;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AccesoHistoriaJpaAdapter implements AccesoHistoriaRepositoryPort {

    private final SpringDataHistoriaClinicaAccesoRepository repository;

    @Override
    public Optional<AccesoHistoria> findFirstByPacienteIdAndUsuarioIdAndAccedidoEnAfterOrderByAccedidoEnDesc(
            UUID pacienteId, UUID usuarioId, OffsetDateTime timestamp) {
        return repository.findFirstByPacienteIdAndUsuarioIdAndAccedidoEnAfterOrderByAccedidoEnDesc(
                pacienteId, usuarioId, timestamp).map(AccesoHistoriaJpaAdapter::toDomain);
    }

    @Override
    public boolean existsByPacienteIdAndUsuarioIdAndAccedidoEnAfter(UUID pacienteId, UUID usuarioId, OffsetDateTime timestamp) {
        return repository.existsByPacienteIdAndUsuarioIdAndAccedidoEnAfter(pacienteId, usuarioId, timestamp);
    }

    @Override
    public AccesoHistoria save(AccesoHistoria acceso) {
        HistoriaClinicaAccesoEntity entity = toEntity(acceso);
        HistoriaClinicaAccesoEntity guardado = repository.save(entity);
        return toDomain(guardado);
    }

    @Override
    public List<AccesoHistoria> findByPacienteIdOrderByAccedidoEnDesc(UUID pacienteId) {
        return repository.findByPacienteIdOrderByAccedidoEnDesc(pacienteId)
                .stream()
                .map(AccesoHistoriaJpaAdapter::toDomain)
                .toList();
    }

    @Override
    public List<AccesoHistoria> findTop200ByOrderByAccedidoEnDesc() {
        return repository.findTop200ByOrderByAccedidoEnDesc()
                .stream()
                .map(AccesoHistoriaJpaAdapter::toDomain)
                .toList();
    }

    private static AccesoHistoria toDomain(HistoriaClinicaAccesoEntity entity) {
        if (entity == null) return null;
        return AccesoHistoria.builder()
                .id(entity.getId())
                .pacienteId(entity.getPacienteId())
                .usuarioId(entity.getUsuarioId())
                .usuarioNombre(entity.getUsuarioNombre())
                .usuarioEmail(entity.getUsuarioEmail())
                .usuarioRol(entity.getUsuarioRol())
                .sedeId(entity.getSedeId())
                .ip(entity.getIp())
                .accedidoEn(entity.getAccedidoEn())
                .build();
    }

    private static HistoriaClinicaAccesoEntity toEntity(AccesoHistoria domain) {
        if (domain == null) return null;
        return HistoriaClinicaAccesoEntity.builder()
                .id(domain.getId())
                .pacienteId(domain.getPacienteId())
                .usuarioId(domain.getUsuarioId())
                .usuarioNombre(domain.getUsuarioNombre())
                .usuarioEmail(domain.getUsuarioEmail())
                .usuarioRol(domain.getUsuarioRol())
                .sedeId(domain.getSedeId())
                .ip(domain.getIp())
                .accedidoEn(domain.getAccedidoEn())
                .build();
    }
}