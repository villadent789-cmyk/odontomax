package com.odontomax.agenda.infrastructure.persistence.mapper;

import com.odontomax.agenda.application.dto.response.HorarioEspecialistaResponseDTO;
import com.odontomax.agenda.domain.model.HorarioEspecialista;
import com.odontomax.agenda.infrastructure.persistence.entity.EspecialistaHorarioEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface HorarioEspecialistaMapper {

    HorarioEspecialista toDomain(EspecialistaHorarioEntity entity);

    EspecialistaHorarioEntity toEntity(HorarioEspecialista domain);

    HorarioEspecialistaResponseDTO toResponseDTO(HorarioEspecialista domain);
}
