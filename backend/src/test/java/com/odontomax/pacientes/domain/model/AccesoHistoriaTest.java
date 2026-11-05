package com.odontomax.pacientes.domain.model;

import com.odontomax.identidad.api.ClinicaUserPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AccesoHistoriaTest {

    @Test
    @DisplayName("registrar: con usuario asigna datos del usuario y sede del paciente")
    void registrarConUsuario() {
        UUID pacienteId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        ClinicaUserPrincipal user = new ClinicaUserPrincipal(
                usuarioId,
                "dra.ana@odontomax.pe",
                "Dra. Ana",
                "ODONTOLOGO",
                "sede-chilca-1"
        );

        AccesoHistoria acceso = AccesoHistoria.registrar(pacienteId, user, "sede-chilca-1", "192.168.1.50");

        assertNotNull(acceso.getId());
        assertEquals(pacienteId, acceso.getPacienteId());
        assertEquals(usuarioId, acceso.getUsuarioId());
        assertEquals("Dra. Ana", acceso.getUsuarioNombre());
        assertEquals("dra.ana@odontomax.pe", acceso.getUsuarioEmail());
        assertEquals("ODONTOLOGO", acceso.getUsuarioRol());
        assertEquals("sede-chilca-1", acceso.getSedeId());
        assertEquals("192.168.1.50", acceso.getIp());
        assertNotNull(acceso.getAccedidoEn());
    }

    @Test
    @DisplayName("registrar: sin usuario asigna valores por defecto y fallback de IP a 127.0.0.1")
    void registrarSinUsuario() {
        UUID pacienteId = UUID.randomUUID();

        AccesoHistoria acceso = AccesoHistoria.registrar(pacienteId, null, "sede-tambo", null);

        assertNotNull(acceso.getId());
        assertNotNull(acceso.getUsuarioId());
        assertEquals("Usuario Desconocido", acceso.getUsuarioNombre());
        assertEquals("desconocido@odontomax.pe", acceso.getUsuarioEmail());
        assertEquals("ANONYMOUS", acceso.getUsuarioRol());
        assertEquals("sede-tambo", acceso.getSedeId());
        assertEquals("127.0.0.1", acceso.getIp());
    }
}