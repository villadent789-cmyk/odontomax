package com.odontomax.agenda.infrastructure.persistence.mapper;

import com.odontomax.agenda.application.dto.response.EspecialistaResponseDTO;
import com.odontomax.agenda.domain.model.Especialista;
import com.odontomax.agenda.infrastructure.persistence.entity.EspecialistaEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface EspecialistaMapper {

    Especialista toDomain(EspecialistaEntity entity);

    EspecialistaEntity toEntity(Especialista domain);

    EspecialistaResponseDTO toResponseDTO(Especialista domain);
}
