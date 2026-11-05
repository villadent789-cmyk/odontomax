package com.odontomax.identidad.application.service;

import com.odontomax.identidad.api.UsuarioModuloDTO;
import com.odontomax.identidad.application.port.out.UsuarioRepository;
import com.odontomax.identidad.domain.model.Usuario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsultaUsuarioModuloServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private ConsultaUsuarioModuloService service;

    @Test
    @DisplayName("buscarPorId expone id, nombre, email, rol, sede y estado; nunca credenciales")
    void buscarPorIdMapeaDatosPublicos() {
        UUID id = UUID.randomUUID();
        when(usuarioRepository.findById(id)).thenReturn(Optional.of(Usuario.builder()
                .id(id).nombre("Dra. Ana").email("ana@odontomax.pe").rol("ODONTOLOGO").sedeId("sede-1")
                .activo(false).passwordHash("$2a$10$hash").totpSecret("SECRETO").build()));

        Optional<UsuarioModuloDTO> res = service.buscarPorId(id);

        assertThat(res).contains(new UsuarioModuloDTO(id, "Dra. Ana", "ana@odontomax.pe", "ODONTOLOGO", "sede-1", false));
    }

    @Test
    @DisplayName("buscarPorId devuelve vacío si el usuario no existe")
    void buscarPorIdInexistente() {
        UUID id = UUID.randomUUID();
        when(usuarioRepository.findById(id)).thenReturn(Optional.empty());

        assertThat(service.buscarPorId(id)).isEmpty();
    }
}
