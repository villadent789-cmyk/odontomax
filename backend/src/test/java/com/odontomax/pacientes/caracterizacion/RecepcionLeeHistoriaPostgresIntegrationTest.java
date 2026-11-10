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
 * Recepción puede VER la historia clínica de los pacientes de su sede (notas, odontograma y formularios clínicos), solo en
 * lectura: no escribe nada, no ve otras sedes y no descarga el PDF ni exporta el expediente.
 */
@Testcontainers(disabledWithoutDocker = true)
class RecepcionLeeHistoriaPostgresIntegrationTest extends PacientesPostgresTestBase {

    @Test
    @DisplayName("Recepción lee notas, odontograma, historial, episodios y formularios del paciente de su sede")
    void recepcionLeeLaHistoriaDeSuSede() throws Exception {
        String sede = crearSede();
        UUID paciente = insertarPaciente(sede, "Ana", "Perez");
        UsuarioPrueba recepcion = crearUsuario("RECEPCION", sede);

        for (String ruta : new String[] {
                "/evoluciones/pacientes/" + paciente,
                "/odontograma/pacientes/" + paciente,
                "/odontograma/pacientes/" + paciente + "/historial",
                "/odontograma/pacientes/" + paciente + "/episodios",
                "/plantillas/registros/paciente/" + paciente,
        }) {
            assertThat(ejecutar(get(ruta), recepcion).getResponse().getStatus()).as(ruta).isEqualTo(200);
        }
    }

    @Test
    @DisplayName("Recepción no ve la historia de un paciente de otra sede")
    void recepcionNoVeOtraSede() throws Exception {
        String miSede = crearSede();
        String otraSede = crearSede();
        UUID ajeno = insertarPaciente(otraSede, "Luis", "Rojas");
        UsuarioPrueba recepcion = crearUsuario("RECEPCION", miSede);

        assertThat(ejecutar(get("/evoluciones/pacientes/" + ajeno), recepcion).getResponse().getStatus()).isEqualTo(403);
        assertThat(ejecutar(get("/odontograma/pacientes/" + ajeno), recepcion).getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("Recepción sigue sin poder escribir en la historia ni descargar el PDF o exportar el expediente")
    void recepcionNoEscribeNiDescarga() throws Exception {
        String sede = crearSede();
        UUID paciente = insertarPaciente(sede, "Eva", "Luna");
        UsuarioPrueba recepcion = crearUsuario("RECEPCION", sede);

        Map<String, Object> nota = Map.of("tipoNota", "EVOLUCION", "subjetivo", "x", "objetivo", "x", "analisis", "x", "plan", "x");
        assertThat(ejecutar(post("/evoluciones/pacientes/" + paciente), recepcion, nota).getResponse().getStatus()).isEqualTo(403);
        Map<String, Object> pieza = Map.of("piezaFdi", 16, "estado", "caries");
        assertThat(ejecutar(post("/odontograma/pacientes/" + paciente + "/pieza"), recepcion, pieza).getResponse().getStatus()).isEqualTo(403);
        assertThat(ejecutar(get("/odontograma/pacientes/" + paciente + "/pdf"), recepcion).getResponse().getStatus()).isEqualTo(403);
        assertThat(ejecutar(get("/pacientes/" + paciente + "/exportar"), recepcion).getResponse().getStatus()).isEqualTo(403);
    }
}
