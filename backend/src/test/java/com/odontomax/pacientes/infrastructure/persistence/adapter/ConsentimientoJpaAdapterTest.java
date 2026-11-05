package com.odontomax.pacientes.infrastructure.persistence.adapter;

import com.odontomax.pacientes.infrastructure.persistence.entity.ConsentimientoEntity;
import com.odontomax.pacientes.infrastructure.persistence.repository.SpringDataConsentimientoRepository;
import com.odontomax.pacientes.domain.model.Consentimiento;
import com.odontomax.pacientes.infrastructure.persistence.mapper.ConsentimientoMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsentimientoJpaAdapterTest {

    @Mock
    private SpringDataConsentimientoRepository repository;

    @Mock
    private ConsentimientoMapper mapper;

    @InjectMocks
    private ConsentimientoJpaAdapter adapter;

    @Test
    @DisplayName("findByPacienteIdOrderByFirmadoEnDesc: busca y mapea a dominio")
    void findByPacienteId() {
        UUID id = UUID.randomUUID();
        ConsentimientoEntity entity = ConsentimientoEntity.builder().id(UUID.randomUUID()).pacienteId(id).build();
        Consentimiento domain = Consentimiento.builder().id(entity.getId()).pacienteId(id).build();

        when(repository.findByPacienteIdOrderByFirmadoEnDesc(id)).thenReturn(List.of(entity));
        when(mapper.toDomain(entity)).thenReturn(domain);

        List<Consentimiento> res = adapter.findByPacienteIdOrderByFirmadoEnDesc(id);
        assertThat(res).hasSize(1);
        assertThat(res.get(0).getId()).isEqualTo(entity.getId());
    }

    @Test
    @DisplayName("save: mapea entidad, persiste y mapea a dominio")
    void save() {
        Consentimiento domain = Consentimiento.builder().id(UUID.randomUUID()).build();
        ConsentimientoEntity entity = ConsentimientoEntity.builder().id(domain.getId()).build();

        when(mapper.toEntity(domain)).thenReturn(entity);
        when(repository.save(entity)).thenReturn(entity);
        when(mapper.toDomain(entity)).thenReturn(domain);

        Consentimiento guardado = adapter.save(domain);
        assertThat(guardado.getId()).isEqualTo(domain.getId());
        verify(repository).save(entity);
    }
}