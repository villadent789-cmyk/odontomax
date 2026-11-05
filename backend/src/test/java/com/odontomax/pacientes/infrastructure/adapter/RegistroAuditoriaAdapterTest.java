package com.odontomax.pacientes.infrastructure.adapter;

import com.odontomax.auditoria.api.RegistrarAuditoriaApi;
import com.odontomax.auditoria.api.RegistroAuditoria;
import com.odontomax.identidad.api.ClinicaUserPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RegistroAuditoriaAdapterTest {

    private final RegistrarAuditoriaApi api = mock(RegistrarAuditoriaApi.class);
    private final RegistroAuditoriaAdapter adapter = new RegistroAuditoriaAdapter(api);

    private RegistroAuditoria capturado() {
        ArgumentCaptor<RegistroAuditoria> captor = ArgumentCaptor.forClass(RegistroAuditoria.class);
        verify(api).registrar(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("Eliminación con principal: usa sus datos, su sede y la ip recibida")
    void eliminacionConPrincipal() {
        UUID pacienteId = UUID.randomUUID();
        ClinicaUserPrincipal admin = new ClinicaUserPrincipal(UUID.randomUUID(), "a@odontomax.pe", "Admin", "ADMIN_GENERAL", "sede-1");

        adapter.registrarEliminacion(admin, pacienteId, "detalle", "10.0.0.1");

        RegistroAuditoria r = capturado();
        assertEquals(admin.id(), r.usuarioId());
        assertEquals("Admin", r.usuarioNombre());
        assertEquals("ADMIN_GENERAL", r.rol());
        assertEquals("sede-1", r.sedeId());
        assertEquals("PACIENTES", r.modulo());
        assertEquals("ELIMINAR_PACIENTE", r.accion());
        assertEquals("pacientes", r.tabla());
        assertEquals(pacienteId.toString(), r.registroId());
        assertEquals("detalle", r.detalle());
        assertEquals("10.0.0.1", r.ip());
    }

    @Test
    @DisplayName("Eliminación sin principal ni ip: SISTEMA, ADMIN_GENERAL, sin sede y 127.0.0.1")
    void eliminacionSinPrincipal() {
        adapter.registrarEliminacion(null, UUID.randomUUID(), "detalle", null);

        RegistroAuditoria r = capturado();
        assertNull(r.usuarioId());
        assertEquals("SISTEMA", r.usuarioNombre());
        assertEquals("ADMIN_GENERAL", r.rol());
        assertNull(r.sedeId());
        assertEquals("127.0.0.1", r.ip());
    }

    @Test
    @DisplayName("Evento clínico sin principal: Sistema, ANONYMOUS y la sede del paciente")
    void eventoClinicoSinPrincipal() {
        adapter.registrarEventoClinico(null, "sede-9", "ACTUALIZAR_ANTECEDENTES", "antecedentes_historial", "reg-1", "d", "1.2.3.4");

        RegistroAuditoria r = capturado();
        assertNull(r.usuarioId());
        assertEquals("Sistema", r.usuarioNombre());
        assertEquals("ANONYMOUS", r.rol());
        assertEquals("sede-9", r.sedeId());
        assertEquals("HISTORIA_CLINICA", r.modulo());
        assertEquals("ACTUALIZAR_ANTECEDENTES", r.accion());
        assertEquals("antecedentes_historial", r.tabla());
        assertEquals("reg-1", r.registroId());
        assertEquals("1.2.3.4", r.ip());
    }

    @Test
    @DisplayName("Exportación ARCO: tabla pacientes, id del paciente y datos del principal")
    void exportacionArco() {
        UUID pacienteId = UUID.randomUUID();
        ClinicaUserPrincipal u = new ClinicaUserPrincipal(UUID.randomUUID(), "a@odontomax.pe", "Admin Sede", "ADMIN_SEDE", "sede-1");

        adapter.registrarExportacionArco(u, "sede-2", pacienteId, "d", "9.9.9.9");

        RegistroAuditoria r = capturado();
        assertEquals(u.id(), r.usuarioId());
        assertEquals("Admin Sede", r.usuarioNombre());
        assertEquals("ADMIN_SEDE", r.rol());
        assertEquals("sede-2", r.sedeId());
        assertEquals("EXPORTAR_ARCO", r.accion());
        assertEquals("pacientes", r.tabla());
        assertEquals(pacienteId.toString(), r.registroId());
    }

    @Test
    @DisplayName("Acceso a historia: copia los datos resueltos del acceso")
    void accesoHistoria() {
        UUID usuarioId = UUID.randomUUID();
        UUID pacienteId = UUID.randomUUID();

        adapter.registrarAccesoHistoria(usuarioId, "Dr. Perez", "ODONTOLOGO", "sede-1", pacienteId, "APERTURA_HISTORIA", "d", "127.0.0.1");

        RegistroAuditoria r = capturado();
        assertEquals(usuarioId, r.usuarioId());
        assertEquals("Dr. Perez", r.usuarioNombre());
        assertEquals("ODONTOLOGO", r.rol());
        assertEquals("sede-1", r.sedeId());
        assertEquals("HISTORIA_CLINICA", r.modulo());
        assertEquals("APERTURA_HISTORIA", r.accion());
        assertEquals("historia_clinica_accesos", r.tabla());
        assertEquals(pacienteId.toString(), r.registroId());
        assertEquals("127.0.0.1", r.ip());
    }

    @Test
    @DisplayName("Un fallo de la API de auditoría no se propaga en ningún flujo")
    void fallosNoSePropagan() {
        doThrow(new RuntimeException("auditoría caída")).when(api).registrar(any());
        UUID id = UUID.randomUUID();

        assertDoesNotThrow(() -> adapter.registrarEliminacion(null, id, "d", null));
        assertDoesNotThrow(() -> adapter.registrarEventoClinico(null, "s", "A", "t", "r", "d", "ip"));
        assertDoesNotThrow(() -> adapter.registrarExportacionArco(null, "s", id, "d", "ip"));
        assertDoesNotThrow(() -> adapter.registrarAccesoHistoria(id, "n", "r", "s", id, "A", "d", "ip"));
        verify(api, times(4)).registrar(any());
    }
}
