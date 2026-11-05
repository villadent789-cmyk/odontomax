package com.odontomax.pacientes.infrastructure.persistence.adapter;

import com.odontomax.pacientes.infrastructure.persistence.entity.AntecedenteHistorialEntity;
import com.odontomax.pacientes.infrastructure.persistence.repository.SpringDataAntecedenteHistorialRepository;
import com.odontomax.pacientes.domain.model.AntecedenteHistorial;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AntecedenteHistorialJpaAdapterTest {

    @Mock
    private SpringDataAntecedenteHistorialRepository repository;

    @InjectMocks
    private AntecedenteHistorialJpaAdapter adapter;

    @Test
    @DisplayName("findByPacienteIdOrderByCreadoEnDesc: mapea entidad a dominio correctamente")
    void findByPacienteIdMapeo() {
        UUID id = UUID.randomUUID();
        AntecedenteHistorialEntity entity = AntecedenteHistorialEntity.builder()
                .id(UUID.randomUUID())
                .pacienteId(id)
                .alergias("Polen")
                .antecedentesMedicos("Gastritis")
                .gestante(false)
                .motivoCambio("Ingreso")
                .modificadoPorNombre("Dr. Lopez")
                .creadoEn(OffsetDateTime.now())
                .build();
        when(repository.findByPacienteIdOrderByCreadoEnDesc(id)).thenReturn(List.of(entity));

        List<AntecedenteHistorial> list = adapter.findByPacienteIdOrderByCreadoEnDesc(id);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).getAlergias()).isEqualTo("Polen");
        assertThat(list.get(0).getAntecedentesMedicos()).isEqualTo("Gastritis");
    }

    @Test
    @DisplayName("save: mapea dominio a entidad y entidad guardada a dominio")
    void saveMapeo() {
        UUID id = UUID.randomUUID();
        AntecedenteHistorial domain = AntecedenteHistorial.builder()
                .pacienteId(id)
                .alergias("Polen")
                .motivoCambio("Ingreso")
                .build();

        AntecedenteHistorialEntity savedEntity = AntecedenteHistorialEntity.builder()
                .id(UUID.randomUUID())
                .pacienteId(id)
                .alergias("Polen")
                .motivoCambio("Ingreso")
                .creadoEn(OffsetDateTime.now())
                .build();
        when(repository.save(any())).thenReturn(savedEntity);

        AntecedenteHistorial res = adapter.save(domain);
        assertThat(res.getId()).isNotNull();
        assertThat(res.getAlergias()).isEqualTo("Polen");
    }
}