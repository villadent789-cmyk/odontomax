package com.odontomax.pacientes.caracterizacion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Una persona con cargo principal y funciones adicionales (odontólogo + recepcionista) acumula los permisos de ambas con
 * una sola cuenta.
 */
@Testcontainers(disabledWithoutDocker = true)
class FuncionesAdicionalesPostgresIntegrationTest extends PacientesPostgresTestBase {

    private UsuarioPrueba conFunciones(String principal, String adicionales, String sede) {
        UsuarioPrueba u = crearUsuario(principal, sede);
        jdbc.update("UPDATE usuarios SET roles_adicionales = ? WHERE id = ?", adicionales, u.id());
        return u;
    }

    @Test
    @DisplayName("Odontólogo con función de recepción ve a todos los pacientes de su sede, no solo a los que atiende")
    void odontologoMasRecepcionVeTodosLosPacientes() throws Exception {
        String sede = crearSede();
        UUID ajeno = insertarPaciente(sede, "Eva", "Ruiz");
        UsuarioPrueba solo = crearUsuario("ODONTOLOGO", sede);
        UsuarioPrueba doble = conFunciones("ODONTOLOGO", "RECEPCION", sede);

        assertThat(ejecutar(get("/evoluciones/pacientes/" + ajeno), solo).getResponse().getStatus()).isEqualTo(403);
        assertThat(ejecutar(get("/evoluciones/pacientes/" + ajeno), doble).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Recepcionista con función de odontólogo puede descargar el PDF del odontograma; sola, no")
    void recepcionMasOdontologoDescargaElPdf() throws Exception {
        String sede = crearSede();
        UUID paciente = insertarPaciente(sede, "Ana", "Paz");
        UsuarioPrueba soloRecepcion = crearUsuario("RECEPCION", sede);
        UsuarioPrueba doble = conFunciones("RECEPCION", "ODONTOLOGO", sede);

        assertThat(ejecutar(get("/odontograma/pacientes/" + paciente + "/pdf"), soloRecepcion).getResponse().getStatus()).isEqualTo(403);
        assertThat(ejecutar(get("/odontograma/pacientes/" + paciente + "/pdf"), doble).getResponse().getStatus()).isNotEqualTo(403);
    }

    @Test
    @DisplayName("Las funciones adicionales solo admiten ODONTOLOGO y RECEPCION (restricción de la base de datos)")
    void laBaseRechazaFuncionesInvalidas() {
        String sede = crearSede();
        UsuarioPrueba u = crearUsuario("ADMIN_SEDE", sede);
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class,
                () -> jdbc.update("UPDATE usuarios SET roles_adicionales = 'ADMIN_GENERAL' WHERE id = ?", u.id()));
    }
}
