package com.odontomax.pacientes.caracterizacion;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Caracterizacion de /historia-clinica: apertura de expediente con deduplicacion de 5 minutos por (paciente, usuario),
 * respuesta 201 siempre, listado solo para ADMIN_GENERAL y tabla inmutable.
 */
@Testcontainers(disabledWithoutDocker = true)
class HistoriaAccesosCaracterizacionTest extends PacientesPostgresTestBase {

    private static final String IP = "10.44.0.9";
    private static final List<String> CLAVES_ACCESO = List.of("id", "pacienteId", "usuarioId", "usuarioNombre", "usuarioEmail",
            "usuarioRol", "sedeId", "ip", "accedidoEn");

    private MvcResult abrir(UsuarioPrueba u, UUID id, String ip) throws Exception {
        return ejecutar(post("/historia-clinica/pacientes/" + id + "/apertura"), u, ip, null);
    }

    private int accesos(UUID pacienteId) {
        return contar("SELECT count(*) FROM historia_clinica_accesos WHERE paciente_id = ?", pacienteId);
    }

    // ------------------------------------------------------------------ apertura

    @Test
    @DisplayName("Apertura: 201 con las 9 claves para los 4 roles; guarda usuario, nombre, email, rol, SEDE DEL PACIENTE e ip de la peticion")
    void aperturaPorRoles() throws Exception {
        String sede = crearSede();
        UUID id = insertarPaciente(sede, "Abre", "Expediente");
        for (String rol : List.of("ADMIN_GENERAL", "ADMIN_SEDE", "ODONTOLOGO", "RECEPCION")) {
            UsuarioPrueba u = crearUsuario(rol, "ADMIN_GENERAL".equals(rol) ? null : sede);
            if ("ODONTOLOGO".equals(rol)) vincularCita(id, u);
            MvcResult r = abrir(u, id, IP);

            assertThat(r.getResponse().getStatus()).as(rol).isEqualTo(201);
            JsonNode j = leer(r);
            assertThat(campos(j)).containsExactlyInAnyOrderElementsOf(CLAVES_ACCESO);
            assertThat(j.get("pacienteId").asText()).isEqualTo(id.toString());
            assertThat(j.get("usuarioId").asText()).isEqualTo(u.id().toString());
            assertThat(j.get("usuarioNombre").asText()).isEqualTo(u.nombre());
            assertThat(j.get("usuarioEmail").asText()).isEqualTo(u.email());
            assertThat(j.get("usuarioRol").asText()).isEqualTo(rol);
            assertThat(j.get("sedeId").asText()).as("sede del paciente, tambien para ADMIN_GENERAL").isEqualTo(sede);
            assertThat(j.get("ip").asText()).isEqualTo(IP);
            assertThat(OffsetDateTime.parse(j.get("accedidoEn").asText())).isBetween(OffsetDateTime.now().minusMinutes(2),
                    OffsetDateTime.now().plusMinutes(1));

            Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM historia_clinica_accesos WHERE id = ?", UUID.fromString(j.get("id").asText()));
            assertThat(fila.get("usuario_id")).isEqualTo(u.id());
            assertThat(fila.get("sede_id")).isEqualTo(sede);
            assertThat(fila.get("ip")).isEqualTo(IP);
        }
        assertThat(accesos(id)).isEqualTo(4);
    }

    @Test
    @DisplayName("Segunda apertura del mismo usuario dentro de 5 minutos: 201 con el MISMO id, sin fila nueva ni auditoria; otro usuario si crea la suya")
    void aperturaDeduplicada() throws Exception {
        String sede = crearSede();
        UsuarioPrueba a = crearUsuario("ODONTOLOGO", sede);
        UsuarioPrueba b = crearUsuario("RECEPCION", sede);
        UUID id = insertarPaciente(sede, "Dedupe", "Apertura");
        vincularCita(id, a);

        MvcResult primera = abrir(a, id, IP);
        MvcResult repetida = abrir(a, id, "10.99.99.99");
        assertThat(primera.getResponse().getStatus()).isEqualTo(201);
        assertThat(repetida.getResponse().getStatus()).as("201 tambien cuando devuelve el acceso deduplicado").isEqualTo(201);
        assertThat(leer(repetida).get("id").asText()).isEqualTo(leer(primera).get("id").asText());
        assertThat(leer(repetida).get("ip").asText()).as("conserva la IP del primero").isEqualTo(IP);
        assertThat(accesos(id)).isEqualTo(1);
        assertThat(filasAuditoria("APERTURA_HISTORIA", id.toString())).hasSize(1);

        MvcResult deB = abrir(b, id, IP);
        assertThat(leer(deB).get("id").asText()).isNotEqualTo(leer(primera).get("id").asText());
        assertThat(leer(deB).get("usuarioId").asText()).isEqualTo(b.id().toString());
        assertThat(accesos(id)).isEqualTo(2);

        // el acceso de B no reemplaza al de A: A sigue recibiendo el suyo
        assertThat(leer(abrir(a, id, IP)).get("id").asText()).isEqualTo(leer(primera).get("id").asText());
        assertThat(accesos(id)).isEqualTo(2);
        assertThat(filasAuditoria("APERTURA_HISTORIA", id.toString())).hasSize(2);

        // otro paciente del mismo usuario: no se deduplica contra el primero
        UUID otro = insertarPaciente(sede, "Dedupe", "Otro");
        vincularCita(otro, a);
        assertThat(leer(abrir(a, otro, IP)).get("id").asText()).isNotEqualTo(leer(primera).get("id").asText());
    }

    @Test
    @DisplayName("Ventana de 5 minutos: un acceso propio de hace 4 minutos se reutiliza; uno de hace 6 minutos no (crea uno nuevo)")
    void ventanaDeCincoMinutos() throws Exception {
        String sede = crearSede();
        UsuarioPrueba reciente = crearUsuario("ODONTOLOGO", sede);
        UsuarioPrueba antiguo = crearUsuario("ODONTOLOGO", sede);
        UUID id = insertarPaciente(sede, "Ventana", "Acceso");
        vincularCita(id, reciente);
        vincularCita(id, antiguo);
        UUID dentro = insertarAcceso(id, reciente, sede, "10.0.0.1", 4);
        UUID fuera = insertarAcceso(id, antiguo, sede, "10.0.0.2", 6);

        MvcResult r1 = abrir(reciente, id, IP);
        assertThat(leer(r1).get("id").asText()).isEqualTo(dentro.toString());
        assertThat(leer(r1).get("ip").asText()).isEqualTo("10.0.0.1");

        MvcResult r2 = abrir(antiguo, id, IP);
        assertThat(r2.getResponse().getStatus()).isEqualTo(201);
        assertThat(leer(r2).get("id").asText()).isNotEqualTo(fuera.toString());
        assertThat(leer(r2).get("ip").asText()).isEqualTo(IP);
        assertThat(accesos(id)).isEqualTo(3);
    }

    @Test
    @DisplayName("Apertura: sede ajena 403, paciente inexistente 400 con el id, sin token 401; no se crea ningun acceso")
    void aperturaErrores() throws Exception {
        String sedeA = crearSede();
        String sedeB = crearSede();
        UUID id = insertarPaciente(sedeA, "Ajeno", "Apertura");
        for (String rol : List.of("ADMIN_SEDE", "ODONTOLOGO", "RECEPCION")) {
            assertThat(abrir(crearUsuario(rol, sedeB), id, IP).getResponse().getStatus()).as(rol).isEqualTo(403);
        }
        UUID inexistente = UUID.randomUUID();
        MvcResult r = abrir(adminGeneral(), inexistente, IP);
        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        assertThat(leer(r).get("message").asText()).isEqualTo("No se encontró el paciente con ID: " + inexistente);
        assertThat(abrir(null, id, IP).getResponse().getStatus()).isEqualTo(401);
        assertThat(accesos(id)).isZero();
        assertThat(filasAuditoria("APERTURA_HISTORIA", id.toString())).isEmpty();
    }

    // ------------------------------------------------------------------ listado

    @Test
    @DisplayName("GET /accesos?pacienteId: solo ADMIN_GENERAL (otros 403, sin token 401), orden descendente por fecha, 9 claves; paciente sin accesos o inexistente -> []")
    void listadoPorPaciente() throws Exception {
        String sede = crearSede();
        UsuarioPrueba u = crearUsuario("ADMIN_SEDE", sede);
        UUID id = insertarPaciente(sede, "Lista", "Accesos");
        UUID otro = insertarPaciente(sede, "Lista", "Otro");
        UUID medio = insertarAcceso(id, u, sede, "10.0.0.2", 20);
        UUID nuevo = insertarAcceso(id, u, sede, "10.0.0.1", 10);
        UUID viejo = insertarAcceso(id, u, sede, "10.0.0.3", 30);
        insertarAcceso(otro, u, sede, "10.0.0.4", 1);

        MvcResult r = ejecutar(get("/historia-clinica/accesos").param("pacienteId", id.toString()), adminGeneral());
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        JsonNode j = leer(r);
        assertThat(ids(j)).containsExactly(nuevo.toString(), medio.toString(), viejo.toString());
        for (JsonNode n : j) {
            assertThat(campos(n)).containsExactlyInAnyOrderElementsOf(CLAVES_ACCESO);
            assertThat(n.get("pacienteId").asText()).isEqualTo(id.toString());
        }

        for (String rol : List.of("ADMIN_SEDE", "ODONTOLOGO", "RECEPCION")) {
            assertThat(ejecutar(get("/historia-clinica/accesos").param("pacienteId", id.toString()), crearUsuario(rol, sede))
                    .getResponse().getStatus()).as(rol).isEqualTo(403);
        }
        assertThat(ejecutar(get("/historia-clinica/accesos"), null).getResponse().getStatus()).isEqualTo(401);

        UUID sinAccesos = insertarPaciente(sede, "Sin", "Accesos");
        assertThat(leer(ejecutar(get("/historia-clinica/accesos").param("pacienteId", sinAccesos.toString()), adminGeneral()))).isEmpty();
        assertThat(leer(ejecutar(get("/historia-clinica/accesos").param("pacienteId", UUID.randomUUID().toString()), adminGeneral()))).isEmpty();
        MvcResult malo = ejecutar(get("/historia-clinica/accesos").param("pacienteId", "no-es-uuid"), adminGeneral());
        assertThat(malo.getResponse().getStatus()).isEqualTo(400);
        assertThat(leer(malo).get("message").asText()).isEqualTo("El parámetro 'pacienteId' tiene un valor inválido.");
    }

    @Test
    @DisplayName("GET /accesos sin pacienteId: los 200 mas recientes de todos los pacientes, en orden descendente")
    void listadoTopDoscientos() throws Exception {
        String sede = crearSede();
        UsuarioPrueba u = crearUsuario("ADMIN_SEDE", sede);
        UUID id = insertarPaciente(sede, "Muchos", "Accesos");
        for (int i = 0; i < 205; i++) {
            insertarAcceso(id, u, sede, "10.1.0." + (i % 250), 1 + i % 50);
        }

        MvcResult r = ejecutar(get("/historia-clinica/accesos"), adminGeneral());

        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        JsonNode j = leer(r);
        assertThat(j).hasSize(200);
        OffsetDateTime anterior = null;
        for (JsonNode n : j) {
            OffsetDateTime actual = OffsetDateTime.parse(n.get("accedidoEn").asText());
            if (anterior != null) {
                assertThat(actual).isBeforeOrEqualTo(anterior);
            }
            anterior = actual;
        }
    }

    @Test
    @DisplayName("La tabla historia_clinica_accesos es inmutable (V7): UPDATE y DELETE se rechazan")
    void tablaInmutable() {
        String sede = crearSede();
        UsuarioPrueba u = crearUsuario("RECEPCION", sede);
        UUID id = insertarPaciente(sede, "Inmutable", "Accesos");
        UUID acceso = insertarAcceso(id, u, sede, "10.0.0.1", 1);
        assertThatThrownBy(() -> jdbc.update("UPDATE historia_clinica_accesos SET ip = '1.1.1.1' WHERE id = ?", acceso))
                .hasMessageContaining("Registro inmutable");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM historia_clinica_accesos WHERE id = ?", acceso))
                .hasMessageContaining("Registro inmutable");
        assertThat(accesos(id)).isEqualTo(1);
    }
}
