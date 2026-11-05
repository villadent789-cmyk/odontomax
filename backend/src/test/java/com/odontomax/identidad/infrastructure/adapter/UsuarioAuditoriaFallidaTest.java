package com.odontomax.identidad.infrastructure.adapter;

import com.odontomax.application.dto.response.UsuarioResponseDTO;
import com.odontomax.auditoria.api.RegistrarAuditoriaApi;
import com.odontomax.identidad.api.ClinicaUserPrincipal;
import com.odontomax.identidad.application.port.out.PasswordHistorialRepository;
import com.odontomax.identidad.application.port.out.UsuarioRepository;
import com.odontomax.identidad.application.service.UsuarioApplicationService;
import com.odontomax.identidad.domain.model.Usuario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * Caso de UsuarioServiceTest que depende de la captura de errores de auditoría, que ahora vive en
 * {@link RegistroAuditoriaAdapter}: caso de uso real + adaptador real + API de auditoría que falla.
 */
@ExtendWith(MockitoExtension.class)
class UsuarioAuditoriaFallidaTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private PasswordHistorialRepository passwordHistorialRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private RegistrarAuditoriaApi registrarAuditoriaApi;

    @Test
    @DisplayName("Estado: un fallo al auditar no impide el cambio de estado")
    void testCambiarEstadoFalloAuditoriaNoBloquea() {
        UUID adminId = UUID.randomUUID();
        ClinicaUserPrincipal admin = new ClinicaUserPrincipal(adminId, "admin@odontomax.pe", "Admin", "ADMIN_GENERAL", null);
        UUID otroId = UUID.randomUUID();
        Usuario otro = Usuario.builder().id(otroId).nombre("Recepción Chilca").email("rec@odontomax.pe")
                .rol("RECEPCION").sedeId("sede-chilca-1").passwordHash("$2a$10$hashActual").activo(true).build();
        UsuarioApplicationService usuarioService = new UsuarioApplicationService(usuarioRepository, passwordEncoder,
                new RegistroAuditoriaAdapter(registrarAuditoriaApi), passwordHistorialRepository, eventPublisher,
                new InMemoryJwtUserCacheAdapter());

        when(usuarioRepository.findById(otroId)).thenReturn(Optional.of(otro));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("BD de auditoría caída")).when(registrarAuditoriaApi).registrar(any());

        UsuarioResponseDTO res = usuarioService.cambiarEstado(otroId, false, admin, "ip");

        assertFalse(res.isActivo());
    }
}
