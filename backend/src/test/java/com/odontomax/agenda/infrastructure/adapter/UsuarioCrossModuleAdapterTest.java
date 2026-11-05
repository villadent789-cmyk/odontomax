package com.odontomax.agenda.infrastructure.adapter;

import com.odontomax.agenda.application.port.out.UsuarioConsultaPort.UsuarioInfo;
import com.odontomax.identidad.api.ConsultaUsuarioModuloApi;
import com.odontomax.identidad.api.UsuarioModuloDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsuarioCrossModuleAdapterTest {

    @Mock
    private ConsultaUsuarioModuloApi consultaUsuarioModuloApi;

    @InjectMocks
    private UsuarioCrossModuleAdapter adapter;

    @Test
    @DisplayName("findById mapea los 6 campos de UsuarioModuloDTO a UsuarioInfo")
    void mapeaUsuario() {
        UUID id = UUID.randomUUID();
        when(consultaUsuarioModuloApi.buscarPorId(id)).thenReturn(Optional.of(
                new UsuarioModuloDTO(id, "Dr. Doc", "doc@odontomax.pe", "ODONTOLOGO", "sede-1", true)));

        assertThat(adapter.findById(id)).contains(
                new UsuarioInfo(id, "Dr. Doc", "doc@odontomax.pe", "ODONTOLOGO", "sede-1", true));
    }

    @Test
    @DisplayName("findById con id nulo devuelve vacío sin consultar identidad")
    void idNulo() {
        assertThat(adapter.findById(null)).isEmpty();
        verify(consultaUsuarioModuloApi, never()).buscarPorId(any());
    }

    @Test
    @DisplayName("findById devuelve vacío si identidad no encuentra el usuario")
    void inexistente() {
        UUID id = UUID.randomUUID();
        when(consultaUsuarioModuloApi.buscarPorId(id)).thenReturn(Optional.empty());

        assertThat(adapter.findById(id)).isEmpty();
    }
}
