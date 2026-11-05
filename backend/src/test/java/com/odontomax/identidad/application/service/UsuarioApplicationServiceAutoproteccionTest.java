package com.odontomax.identidad.application.service;

import com.odontomax.application.dto.request.UsuarioUpdateRequestDTO;
import com.odontomax.identidad.api.ClinicaUserPrincipal;
import com.odontomax.identidad.application.port.out.JwtUserCachePort;
import com.odontomax.identidad.application.port.out.PasswordHistorialRepository;
import com.odontomax.identidad.application.port.out.RegistroAuditoriaPort;
import com.odontomax.identidad.application.port.out.UsuarioRepository;
import com.odontomax.identidad.domain.model.Usuario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Reescritura 1:1 de UsuarioServiceAutoproteccionTest contra puertos. */
@ExtendWith(MockitoExtension.class)
class UsuarioApplicationServiceAutoproteccionTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private RegistroAuditoriaPort registroAuditoria;
    @Mock private PasswordHistorialRepository passwordHistorialRepository;
    @Mock private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @Mock
    private JwtUserCachePort jwtUserCache;

    @InjectMocks
    private UsuarioApplicationService usuarioService;

    private final UUID adminId = UUID.randomUUID();
    private final ClinicaUserPrincipal admin =
            new ClinicaUserPrincipal(adminId, "admin@odontomax.pe", "Admin", "ADMIN_GENERAL", null);

    private Usuario cuentaAdmin() {
        return Usuario.builder().id(adminId).nombre("Admin").email("admin@odontomax.pe")
                .rol("ADMIN_GENERAL").activo(true).build();
    }

    @Test
    @DisplayName("El administrador no puede quitarse su propio rol de ADMIN_GENERAL")
    void noPuedeDegradarseASiMismo() {
        when(usuarioRepository.findById(adminId)).thenReturn(Optional.of(cuentaAdmin()));

        UsuarioUpdateRequestDTO req = UsuarioUpdateRequestDTO.builder()
                .nombre("Admin").rol("ODONTOLOGO").sedeId("sede-chilca-1").build();

        assertThrows(IllegalArgumentException.class,
                () -> usuarioService.actualizarUsuario(adminId, req, admin, "127.0.0.1"));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("El administrador no puede desactivarse a sí mismo vía edición")
    void noPuedeDesactivarseViaEdicion() {
        when(usuarioRepository.findById(adminId)).thenReturn(Optional.of(cuentaAdmin()));

        UsuarioUpdateRequestDTO req = UsuarioUpdateRequestDTO.builder()
                .nombre("Admin").rol("ADMIN_GENERAL").activo(false).build();

        assertThrows(IllegalArgumentException.class,
                () -> usuarioService.actualizarUsuario(adminId, req, admin, "127.0.0.1"));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("El administrador sí puede cambiar el rol de OTRO administrador")
    void puedeCambiarRolDeOtro() {
        UUID otroId = UUID.randomUUID();
        when(usuarioRepository.findById(otroId)).thenReturn(Optional.of(Usuario.builder()
                .id(otroId).nombre("Otro").email("otro@odontomax.pe").rol("ADMIN_GENERAL").activo(true).build()));
        when(usuarioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UsuarioUpdateRequestDTO req = UsuarioUpdateRequestDTO.builder()
                .nombre("Otro").rol("ADMIN_SEDE").sedeId("sede-chilca-1").build();

        var res = usuarioService.actualizarUsuario(otroId, req, admin, "127.0.0.1");
        assertEquals("ADMIN_SEDE", res.getRol());
    }
}
