package com.odontomax.pacientes.infrastructure.persistence.adapter;

import com.odontomax.pacientes.infrastructure.persistence.entity.PlantillaConsentimientoEntity;
import com.odontomax.pacientes.infrastructure.persistence.repository.SpringDataPlantillaConsentimientoRepository;
import com.odontomax.pacientes.application.port.out.PlantillaConsentimientoRepositoryPort;
import com.odontomax.pacientes.domain.model.PlantillaConsentimiento;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class PlantillaConsentimientoJpaAdapter implements PlantillaConsentimientoRepositoryPort {

    private final SpringDataPlantillaConsentimientoRepository repository;

    @Override
    public List<PlantillaConsentimiento> findByActivoTrueOrderByTituloAsc() {
        return repository.findByActivoTrueOrderByTituloAsc()
                .stream()
                .map(PlantillaConsentimientoJpaAdapter::toDomain)
                .toList();
    }

    private static PlantillaConsentimiento toDomain(PlantillaConsentimientoEntity entity) {
        if (entity == null) return null;
        return new PlantillaConsentimiento(
                entity.getId(),
                entity.getCodigo(),
                entity.getTitulo(),
                entity.getTipo(),
                entity.getContenido(),
                entity.getVersion(),
                entity.getActivo()
        );
    }
}