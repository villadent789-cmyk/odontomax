package com.odontomax.auditoria.infrastructure;

import com.odontomax.auditoria.api.RegistroAuditoria;
import com.odontomax.infrastructure.persistence.entity.AuditoriaLogEntity;
import com.odontomax.infrastructure.persistence.repository.SpringDataAuditoriaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrarAuditoriaJpaAdapterTest {

    @Mock
    private SpringDataAuditoriaRepository auditoriaRepository;

    @InjectMocks
    private RegistrarAuditoriaJpaAdapter adapter;

    @Test
    @DisplayName("Copia los 10 campos 1:1 a AuditoriaLogEntity")
    void copiaLosDiezCampos() {
        UUID usuarioId = UUID.randomUUID();

        adapter.registrar(new RegistroAuditoria(usuarioId, "Ana", "ADMIN_SEDE", "sede-1", "USUARIOS",
                "CREAR_USUARIO", "usuarios", "reg-1", "detalle", "10.0.0.7"));

        ArgumentCaptor<AuditoriaLogEntity> captor = ArgumentCaptor.forClass(AuditoriaLogEntity.class);
        verify(auditoriaRepository).save(captor.capture());
        AuditoriaLogEntity e = captor.getValue();
        assertThat(e.getId()).isNull();
        assertThat(e.getUsuarioId()).isEqualTo(usuarioId);
        assertThat(e.getUsuarioNombre()).isEqualTo("Ana");
        assertThat(e.getRol()).isEqualTo("ADMIN_SEDE");
        assertThat(e.getSedeId()).isEqualTo("sede-1");
        assertThat(e.getModulo()).isEqualTo("USUARIOS");
        assertThat(e.getAccion()).isEqualTo("CREAR_USUARIO");
        assertThat(e.getTabla()).isEqualTo("usuarios");
        assertThat(e.getRegistroId()).isEqualTo("reg-1");
        assertThat(e.getDetalle()).isEqualTo("detalle");
        assertThat(e.getIp()).isEqualTo("10.0.0.7");
    }

    @Test
    @DisplayName("No aplica valores por defecto: los nulos se guardan como nulos")
    void noAplicaDefaults() {
        adapter.registrar(new RegistroAuditoria(null, null, null, null, "AGENDA", "ACCION", null, null, null, null));

        ArgumentCaptor<AuditoriaLogEntity> captor = ArgumentCaptor.forClass(AuditoriaLogEntity.class);
        verify(auditoriaRepository).save(captor.capture());
        AuditoriaLogEntity e = captor.getValue();
        assertThat(e.getUsuarioId()).isNull();
        assertThat(e.getUsuarioNombre()).isNull();
        assertThat(e.getRol()).isNull();
        assertThat(e.getSedeId()).isNull();
        assertThat(e.getTabla()).isNull();
        assertThat(e.getRegistroId()).isNull();
        assertThat(e.getDetalle()).isNull();
        assertThat(e.getIp()).isNull();
    }

    @Test
    @DisplayName("No captura excepciones del repositorio: se propagan al llamador")
    void propagaExcepciones() {
        when(auditoriaRepository.save(any())).thenThrow(new IllegalStateException("fallo BD"));

        assertThatThrownBy(() -> adapter.registrar(
                new RegistroAuditoria(null, null, null, null, "M", "A", null, null, null, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("fallo BD");
    }
}
