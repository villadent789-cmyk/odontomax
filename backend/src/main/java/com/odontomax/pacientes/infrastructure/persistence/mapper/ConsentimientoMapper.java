package com.odontomax.pacientes.infrastructure.persistence.mapper;

import com.odontomax.pacientes.infrastructure.persistence.entity.ConsentimientoEntity;
import com.odontomax.pacientes.domain.model.Consentimiento;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ConsentimientoMapper {
    Consentimiento toDomain(ConsentimientoEntity entity);
    ConsentimientoEntity toEntity(Consentimiento domain);
}