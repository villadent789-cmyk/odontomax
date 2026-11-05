package com.odontomax.identidad.infrastructure.adapter;

import com.odontomax.auditoria.api.RegistrarAuditoriaApi;
import com.odontomax.auditoria.api.RegistroAuditoria;
import com.odontomax.identidad.api.ClinicaUserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RegistroAuditoriaAdapterTest {

    @Mock
    private RegistrarAuditoriaApi api;

    @InjectMocks
    private RegistroAuditoriaAdapter adapter;

    private final ClinicaUserPrincipal ana =
            new ClinicaUserPrincipal(UUID.randomUUID(), "ana@odontomax.pe", "Ana", "ADMIN_SEDE", "sede-1");

    @AfterEach
    void limpiar() {
        RequestContextHolder.resetRequestAttributes();
    }

    private RegistroAuditoria capturado() {
        ArgumentCaptor<RegistroAuditoria> captor = ArgumentCaptor.forClass(RegistroAuditoria.class);
        verify(api).registrar(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("Evento de seguridad con usuario: SEGURIDAD, tabla usuarios, sin registro_id e ip de la petición")
    void eventoSeguridadConUsuario() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("10.20.30.40");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));

        adapter.registrarEventoSeguridad(ana, "CAMBIO_PASSWORD", "detalle");

        assertThat(capturado()).isEqualTo(new RegistroAuditoria(ana.id(), "Ana", "ADMIN_SEDE", "sede-1",
                "SEGURIDAD", "CAMBIO_PASSWORD", "usuarios", null, "detalle", "10.20.30.40"));
    }

    @Test
    @DisplayName("Evento de seguridad sin usuario: 'Sistema' / 'ANONYMOUS' e ip 127.0.0.1 fuera de petición")
    void eventoSeguridadSinUsuario() {
        adapter.registrarEventoSeguridad(null, "PRUEBA", "d");

        assertThat(capturado()).isEqualTo(new RegistroAuditoria(null, "Sistema", "ANONYMOUS", null,
                "SEGURIDAD", "PRUEBA", "usuarios", null, "d", "127.0.0.1"));
    }

    @Test
    @DisplayName("Evento de seguridad: un fallo de escritura se ignora en silencio")
    void eventoSeguridadTragaExcepcion() {
        doThrow(new IllegalStateException("fallo")).when(api).registrar(any());

        assertThatCode(() -> adapter.registrarEventoSeguridad(ana, "A", "d")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Operación de usuarios con principal e ip: copia todos los campos")
    void operacionUsuariosConPrincipal() {
        adapter.registrarOperacionUsuarios(ana, "USUARIOS", "CREAR_USUARIO", "usuarios", "reg-1", "d", "10.9.8.7");

        assertThat(capturado()).isEqualTo(new RegistroAuditoria(ana.id(), "Ana", "ADMIN_SEDE", "sede-1",
                "USUARIOS", "CREAR_USUARIO", "usuarios", "reg-1", "d", "10.9.8.7"));
    }

    @Test
    @DisplayName("Operación de usuarios sin principal ni ip: 'SISTEMA' / 'ADMIN_GENERAL' / '127.0.0.1'")
    void operacionUsuariosDefaults() {
        adapter.registrarOperacionUsuarios(null, "USUARIOS", "CREAR_USUARIO", "usuarios", "reg-1", "d", null);

        assertThat(capturado()).isEqualTo(new RegistroAuditoria(null, "SISTEMA", "ADMIN_GENERAL", null,
                "USUARIOS", "CREAR_USUARIO", "usuarios", "reg-1", "d", "127.0.0.1"));
    }

    @Test
    @DisplayName("Operación de usuarios: un fallo de escritura no se propaga")
    void operacionUsuariosNoPropaga() {
        doThrow(new IllegalStateException("fallo")).when(api).registrar(any());

        assertThatCode(() -> adapter.registrarOperacionUsuarios(ana, "USUARIOS", "A", "usuarios", "r", "d", "1.2.3.4"))
                .doesNotThrowAnyException();
    }
}
