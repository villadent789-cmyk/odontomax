package com.odontomax.identidad.infrastructure.persistence.adapter;

import com.odontomax.identidad.infrastructure.persistence.entity.PasswordHistorialEntity;
import com.odontomax.identidad.infrastructure.persistence.repository.SpringDataPasswordHistorialRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordJpaAdaptersTest {

    @Mock
    private SpringDataPasswordHistorialRepository historialRepository;

    @Test
    @DisplayName("Historial: devuelve los hashes de las últimas 3 en el orden del repositorio")
    void historialDevuelveHashes() {
        UUID usuarioId = UUID.randomUUID();
        when(historialRepository.findTop3ByUsuarioIdOrderByCreadoEnDesc(usuarioId)).thenReturn(List.of(
                PasswordHistorialEntity.builder().passwordHash("h3").build(),
                PasswordHistorialEntity.builder().passwordHash("h2").build()));

        assertThat(new PasswordHistorialJpaAdapter(historialRepository).findUltimosTresHashes(usuarioId))
                .containsExactly("h3", "h2");
    }

    @Test
    @DisplayName("Historial: guardar persiste usuario y hash")
    void historialGuarda() {
        UUID usuarioId = UUID.randomUUID();

        new PasswordHistorialJpaAdapter(historialRepository).guardar(usuarioId, "hash-anterior");

        ArgumentCaptor<PasswordHistorialEntity> captor = ArgumentCaptor.forClass(PasswordHistorialEntity.class);
        verify(historialRepository).save(captor.capture());
        assertThat(captor.getValue().getUsuarioId()).isEqualTo(usuarioId);
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("hash-anterior");
    }

}
