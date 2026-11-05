package com.odontomax.pacientes.infrastructure.persistence.adapter;

import com.odontomax.pacientes.infrastructure.persistence.entity.HistoriaClinicaAccesoEntity;
import com.odontomax.pacientes.infrastructure.persistence.repository.SpringDataHistoriaClinicaAccesoRepository;
import com.odontomax.pacientes.domain.model.AccesoHistoria;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccesoHistoriaJpaAdapterTest {

    @Mock
    private SpringDataHistoriaClinicaAccesoRepository repository;

    @InjectMocks
    private AccesoHistoriaJpaAdapter adapter;

    @Test
    @DisplayName("findFirstByPacienteId... mapea entidad a dominio")
    void findFirst() {
        UUID pacienteId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        OffsetDateTime ventana = OffsetDateTime.now().minusMinutes(5);

        HistoriaClinicaAccesoEntity entity = HistoriaClinicaAccesoEntity.builder()
                .id(UUID.randomUUID())
                .pacienteId(pacienteId)
                .usuarioId(usuarioId)
                .usuarioNombre("Dr. Perez")
                .usuarioEmail("perez@odontomax.pe")
                .usuarioRol("ODONTOLOGO")
                .sedeId("sede-1")
                .ip("127.0.0.1")
                .accedidoEn(OffsetDateTime.now())
                .build();

        when(repository.findFirstByPacienteIdAndUsuarioIdAndAccedidoEnAfterOrderByAccedidoEnDesc(
                eq(pacienteId), eq(usuarioId), eq(ventana))).thenReturn(Optional.of(entity));

        Optional<AccesoHistoria> opt = adapter.findFirstByPacienteIdAndUsuarioIdAndAccedidoEnAfterOrderByAccedidoEnDesc(
                pacienteId, usuarioId, ventana);

        assertThat(opt).isPresent();
        assertThat(opt.get().getId()).isEqualTo(entity.getId());
        assertThat(opt.get().getUsuarioNombre()).isEqualTo("Dr. Perez");
    }

    @Test
    @DisplayName("save: mapea dominio a entidad, persiste y mapea a dominio")
    void save() {
        AccesoHistoria domain = AccesoHistoria.builder()
                .id(UUID.randomUUID())
                .pacienteId(UUID.randomUUID())
                .usuarioId(UUID.randomUUID())
                .usuarioNombre("Dr. Perez")
                .usuarioEmail("perez@odontomax.pe")
                .usuarioRol("ODONTOLOGO")
                .sedeId("sede-1")
                .ip("127.0.0.1")
                .accedidoEn(OffsetDateTime.now())
                .build();

        when(repository.save(any(HistoriaClinicaAccesoEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        AccesoHistoria guardado = adapter.save(domain);
        assertThat(guardado.getId()).isEqualTo(domain.getId());
        verify(repository).save(any(HistoriaClinicaAccesoEntity.class));
    }
}