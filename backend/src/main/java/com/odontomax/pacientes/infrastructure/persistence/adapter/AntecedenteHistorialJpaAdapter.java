package com.odontomax.pacientes.infrastructure.persistence.adapter;

import com.odontomax.pacientes.infrastructure.persistence.entity.AntecedenteHistorialEntity;
import com.odontomax.pacientes.infrastructure.persistence.repository.SpringDataAntecedenteHistorialRepository;
import com.odontomax.pacientes.application.port.out.AntecedenteHistorialRepositoryPort;
import com.odontomax.pacientes.domain.model.AntecedenteHistorial;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AntecedenteHistorialJpaAdapter implements AntecedenteHistorialRepositoryPort {

    private final SpringDataAntecedenteHistorialRepository repository;

    @Override
    public List<AntecedenteHistorial> findByPacienteIdOrderByCreadoEnDesc(UUID pacienteId) {
        return repository.findByPacienteIdOrderByCreadoEnDesc(pacienteId)
                .stream()
                .map(AntecedenteHistorialJpaAdapter::toDomain)
                .toList();
    }

    @Override
    public AntecedenteHistorial save(AntecedenteHistorial domain) {
        AntecedenteHistorialEntity entity = toEntity(domain);
        return toDomain(repository.save(entity));
    }

    private static AntecedenteHistorial toDomain(AntecedenteHistorialEntity entity) {
        if (entity == null) return null;
        return AntecedenteHistorial.builder()
                .id(entity.getId())
                .pacienteId(entity.getPacienteId())
                .alergias(entity.getAlergias())
                .antecedentesMedicos(entity.getAntecedentesMedicos())
                .gestante(entity.getGestante())
                .semanasGestacion(entity.getSemanasGestacion())
                .motivoCambio(entity.getMotivoCambio())
                .modificadoPor(entity.getModificadoPor())
                .modificadoPorNombre(entity.getModificadoPorNombre())
                .creadoEn(entity.getCreadoEn())
                .build();
    }

    private static AntecedenteHistorialEntity toEntity(AntecedenteHistorial domain) {
        if (domain == null) return null;
        return AntecedenteHistorialEntity.builder()
                .id(domain.getId())
                .pacienteId(domain.getPacienteId())
                .alergias(domain.getAlergias())
                .antecedentesMedicos(domain.getAntecedentesMedicos())
                .gestante(domain.getGestante())
                .semanasGestacion(domain.getSemanasGestacion())
                .motivoCambio(domain.getMotivoCambio())
                .modificadoPor(domain.getModificadoPor())
                .modificadoPorNombre(domain.getModificadoPorNombre())
                .creadoEn(domain.getCreadoEn())
                .build();
    }
}