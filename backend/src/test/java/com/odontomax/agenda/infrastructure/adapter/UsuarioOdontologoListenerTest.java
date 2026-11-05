package com.odontomax.agenda.infrastructure.adapter;

import com.odontomax.agenda.application.service.EspecialistaApplicationService;
import com.odontomax.identidad.api.event.UsuarioEstadoModificado;
import com.odontomax.identidad.api.event.UsuarioOdontologoRegistrado;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UsuarioOdontologoListenerTest {

    @Mock
    private EspecialistaApplicationService especialistaService;

    @InjectMocks
    private UsuarioOdontologoListener listener;

    @Test
    @DisplayName("onUsuarioOdontologoRegistrado: captura y delega en asegurarPerfilDeOdontologo con parámetros exactos")
    void testOnUsuarioOdontologoRegistrado_parametrosCompletos() {
        UUID usuarioId = UUID.randomUUID();
        UsuarioOdontologoRegistrado event = new UsuarioOdontologoRegistrado(
                usuarioId, "Dr. Alex Ríos", "alex@odontomax.pe", "sede-chilca-1"
        );

        listener.onUsuarioOdontologoRegistrado(event);

        org.mockito.ArgumentCaptor<UUID> idCaptor = org.mockito.ArgumentCaptor.forClass(UUID.class);
        org.mockito.ArgumentCaptor<String> nombreCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.ArgumentCaptor<String> emailCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.ArgumentCaptor<String> sedeCaptor = org.mockito.ArgumentCaptor.forClass(String.class);

        verify(especialistaService).asegurarPerfilDeOdontologo(
                idCaptor.capture(), nombreCaptor.capture(), emailCaptor.capture(), sedeCaptor.capture(), org.mockito.ArgumentMatchers.isNull()
        );

        org.junit.jupiter.api.Assertions.assertNotNull(event);
        org.junit.jupiter.api.Assertions.assertNotNull(event.usuarioId());
        org.junit.jupiter.api.Assertions.assertNotNull(event.nombre());
        org.junit.jupiter.api.Assertions.assertNotNull(event.email());
        org.junit.jupiter.api.Assertions.assertNotNull(event.sedeId());
        org.junit.jupiter.api.Assertions.assertEquals(usuarioId, idCaptor.getValue());
        org.junit.jupiter.api.Assertions.assertEquals("Dr. Alex Ríos", nombreCaptor.getValue());
        org.junit.jupiter.api.Assertions.assertEquals("alex@odontomax.pe", emailCaptor.getValue());
        org.junit.jupiter.api.Assertions.assertEquals("sede-chilca-1", sedeCaptor.getValue());
    }

    @Test
    @DisplayName("onUsuarioOdontologoRegistrado: maneja evento con sede específica y preserva datos")
    void testOnUsuarioOdontologoRegistrado_otraSede() {
        UUID usuarioId = UUID.randomUUID();
        UsuarioOdontologoRegistrado event = new UsuarioOdontologoRegistrado(
                usuarioId, "Dra. Carmen Mendoza", "carmen@odontomax.pe", "sede-eltambo"
        );

        listener.onUsuarioOdontologoRegistrado(event);

        org.mockito.ArgumentCaptor<UUID> idCaptor = org.mockito.ArgumentCaptor.forClass(UUID.class);
        org.mockito.ArgumentCaptor<String> nombreCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.ArgumentCaptor<String> emailCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.ArgumentCaptor<String> sedeCaptor = org.mockito.ArgumentCaptor.forClass(String.class);

        verify(especialistaService).asegurarPerfilDeOdontologo(
                idCaptor.capture(), nombreCaptor.capture(), emailCaptor.capture(), sedeCaptor.capture(), org.mockito.ArgumentMatchers.isNull()
        );

        org.junit.jupiter.api.Assertions.assertNotNull(event);
        org.junit.jupiter.api.Assertions.assertNotNull(idCaptor.getValue());
        org.junit.jupiter.api.Assertions.assertEquals(usuarioId, idCaptor.getValue());
        org.junit.jupiter.api.Assertions.assertEquals("Dra. Carmen Mendoza", nombreCaptor.getValue());
        org.junit.jupiter.api.Assertions.assertEquals("carmen@odontomax.pe", emailCaptor.getValue());
        org.junit.jupiter.api.Assertions.assertEquals("sede-eltambo", sedeCaptor.getValue());
    }

    @Test
    @DisplayName("onUsuarioEstadoModificado: desactivación de cuenta delega con activo=false")
    void testOnUsuarioEstadoModificado_desactivacion() {
        UUID usuarioId = UUID.randomUUID();
        UsuarioEstadoModificado event = new UsuarioEstadoModificado(usuarioId, false);

        listener.onUsuarioEstadoModificado(event);

        org.mockito.ArgumentCaptor<UUID> idCaptor = org.mockito.ArgumentCaptor.forClass(UUID.class);
        org.mockito.ArgumentCaptor<Boolean> activoCaptor = org.mockito.ArgumentCaptor.forClass(Boolean.class);

        verify(especialistaService).sincronizarEstadoConUsuario(idCaptor.capture(), activoCaptor.capture());

        org.junit.jupiter.api.Assertions.assertNotNull(event);
        org.junit.jupiter.api.Assertions.assertNotNull(event.usuarioId());
        org.junit.jupiter.api.Assertions.assertEquals(usuarioId, idCaptor.getValue());
        org.junit.jupiter.api.Assertions.assertFalse(activoCaptor.getValue());
        org.junit.jupiter.api.Assertions.assertEquals(Boolean.FALSE, activoCaptor.getValue());
    }

    @Test
    @DisplayName("onUsuarioEstadoModificado: activación de cuenta delega con activo=true")
    void testOnUsuarioEstadoModificado_activacion() {
        UUID usuarioId = UUID.randomUUID();
        UsuarioEstadoModificado event = new UsuarioEstadoModificado(usuarioId, true);

        listener.onUsuarioEstadoModificado(event);

        org.mockito.ArgumentCaptor<UUID> idCaptor = org.mockito.ArgumentCaptor.forClass(UUID.class);
        org.mockito.ArgumentCaptor<Boolean> activoCaptor = org.mockito.ArgumentCaptor.forClass(Boolean.class);

        verify(especialistaService).sincronizarEstadoConUsuario(idCaptor.capture(), activoCaptor.capture());

        org.junit.jupiter.api.Assertions.assertNotNull(event);
        org.junit.jupiter.api.Assertions.assertNotNull(event.usuarioId());
        org.junit.jupiter.api.Assertions.assertEquals(usuarioId, idCaptor.getValue());
        org.junit.jupiter.api.Assertions.assertTrue(activoCaptor.getValue());
        org.junit.jupiter.api.Assertions.assertEquals(Boolean.TRUE, activoCaptor.getValue());
    }
}
