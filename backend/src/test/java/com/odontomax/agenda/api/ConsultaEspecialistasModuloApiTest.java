package com.odontomax.agenda.api;

import com.odontomax.identidad.api.ClinicaUserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

class ConsultaEspecialistasModuloApiTest {

    private final UUID usuarioId = UUID.randomUUID();
    private final UUID pacienteId = UUID.randomUUID();
    private final ConsultaEspecialistasModuloApi api = mock(ConsultaEspecialistasModuloApi.class, CALLS_REAL_METHODS);

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private void autenticar(String rol) {
        ClinicaUserPrincipal principal = new ClinicaUserPrincipal(usuarioId, rol + "@odontomax.pe", rol, rol, "sede-chilca-1");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + rol))));
    }

    @Test
    @DisplayName("Un odontólogo sin cita con el paciente recibe AccessDenied")
    void odontologoSinCita() {
        autenticar("ODONTOLOGO");
        doReturn(false).when(api).odontologoAtiendeAlPaciente(usuarioId, pacienteId);

        assertThrows(AccessDeniedException.class, () -> api.exigirAccesoDeOdontologo(pacienteId));
    }

    @Test
    @DisplayName("Un odontólogo con cita con el paciente pasa")
    void odontologoConCita() {
        autenticar("ODONTOLOGO");
        doReturn(true).when(api).odontologoAtiendeAlPaciente(usuarioId, pacienteId);

        assertDoesNotThrow(() -> api.exigirAccesoDeOdontologo(pacienteId));
    }

    @Test
    @DisplayName("Recepción y administradores no se filtran por doctor")
    void otrosRolesPasan() {
        for (String rol : List.of("RECEPCION", "ADMIN_SEDE", "ADMIN_GENERAL")) {
            autenticar(rol);
            assertDoesNotThrow(() -> api.exigirAccesoDeOdontologo(pacienteId), rol);
        }
    }
}
