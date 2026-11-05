package com.odontomax.pacientes.infrastructure.persistence.adapter;

import com.odontomax.pacientes.infrastructure.persistence.entity.ConsentimientoEntity;
import com.odontomax.pacientes.infrastructure.persistence.repository.SpringDataConsentimientoRepository;
import com.odontomax.pacientes.application.port.out.ConsentimientoRepositoryPort;
import com.odontomax.pacientes.domain.model.Consentimiento;
import com.odontomax.pacientes.infrastructure.persistence.mapper.ConsentimientoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ConsentimientoJpaAdapter implements ConsentimientoRepositoryPort {

    private final SpringDataConsentimientoRepository repository;
    private final ConsentimientoMapper mapper;

    @Override
    public List<Consentimiento> findByPacienteIdOrderByFirmadoEnDesc(UUID pacienteId) {
        return repository.findByPacienteIdOrderByFirmadoEnDesc(pacienteId)
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public Consentimiento save(Consentimiento consentimiento) {
        ConsentimientoEntity entity = mapper.toEntity(consentimiento);
        ConsentimientoEntity guardado = repository.save(entity);
        return mapper.toDomain(guardado);
    }

    @Override
    public java.util.Optional<Consentimiento> findById(UUID id) {
        return repository.findById(id).map(mapper::toDomain);
    }
}