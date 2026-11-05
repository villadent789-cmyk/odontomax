package com.odontomax.pacientes.caracterizacion;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Nombre, DNI y fecha de nacimiento solo los cambia el dueño; el resto de roles pide la corrección y el dueño la
 * aprueba (se aplica) o la rechaza (no cambia nada).
 */
@Testcontainers(disabledWithoutDocker = true)
class SolicitudCorreccionPostgresIntegrationTest extends PacientesPostgresTestBase {

    private Map<String, Object> cuerpoEdicion(UUID id, String sede, String nombres, String apellidos) {
        Map<String, Object> m = new HashMap<>();
        m.put("dni", pacienteBd(id).get("dni"));
        m.put("nombres", nombres);
        m.put("apellidos", apellidos);
        m.put("sedeId", sede);
        return m;
    }

    @Test
    @DisplayName("Un doctor no cambia el nombre directamente (403) pero sí otros datos; el dueño sí cambia el nombre")
    void cambioDirectoDeIdentidad() throws Exception {
        String sede = crearSede();
        UUID id = insertarPaciente(sede, "Ana", "Perez");
        UsuarioPrueba doctor = crearUsuario("ODONTOLOGO", sede);
        vincularCita(id, doctor);

        MvcResult sinPermiso = ejecutar(put("/pacientes/" + id), doctor, cuerpoEdicion(id, sede, "Anna", "Perez"));
        assertThat(sinPermiso.getResponse().getStatus()).isEqualTo(403);
        assertThat(leer(sinPermiso).get("message").asText()).contains("autorización del dueño");
        assertThat(pacienteBd(id).get("nombres")).isEqualTo("Ana");

        Map<String, Object> otroDato = cuerpoEdicion(id, sede, "Ana", "Perez");
        otroDato.put("telefono", "987654321");
        assertThat(ejecutar(put("/pacientes/" + id), doctor, otroDato).getResponse().getStatus()).isEqualTo(200);

        assertThat(ejecutar(put("/pacientes/" + id), adminGeneral(), cuerpoEdicion(id, sede, "Anna", "Perez")).getResponse().getStatus())
                .isEqualTo(200);
        assertThat(pacienteBd(id).get("nombres")).isEqualTo("Anna");
    }

    @Test
    @DisplayName("Solicitar → el dueño aprueba → el cambio se aplica, queda constancia y no se puede resolver dos veces")
    void solicitarYAprobar() throws Exception {
        String sede = crearSede();
        UUID id = insertarPaciente(sede, "Ana", "Perez");
        UsuarioPrueba doctor = crearUsuario("ODONTOLOGO", sede);
        vincularCita(id, doctor);
        UsuarioPrueba dueno = adminGeneral();

        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("nombres", "Anna");
        cuerpo.put("motivo", "Error al escribir el nombre");
        MvcResult creada = ejecutar(post("/pacientes/" + id + "/solicitudes-correccion"), doctor, cuerpo);
        assertThat(creada.getResponse().getStatus()).isEqualTo(201);
        JsonNode s = leer(creada);
        assertThat(s.get("estado").asText()).isEqualTo("PENDIENTE");
        assertThat(s.get("nombresAnterior").asText()).isEqualTo("Ana");
        assertThat(s.get("nombresNuevo").asText()).isEqualTo("Anna");
        String solicitudId = s.get("id").asText();
        assertThat(pacienteBd(id).get("nombres")).isEqualTo("Ana");

        // el doctor no puede resolverla; el dueño la ve pendiente
        assertThat(ejecutar(post("/pacientes/solicitudes-correccion/" + solicitudId + "/aprobar"), doctor, Map.of()).getResponse().getStatus())
                .isEqualTo(403);
        JsonNode pendientes = leer(ejecutar(get("/pacientes/solicitudes-correccion/pendientes"), dueno, null));
        assertThat(pendientes.findValuesAsText("id")).contains(solicitudId);

        MvcResult aprobada = ejecutar(post("/pacientes/solicitudes-correccion/" + solicitudId + "/aprobar"), dueno, Map.of("comentario", "Autorizado"));
        assertThat(aprobada.getResponse().getStatus()).isEqualTo(200);
        assertThat(leer(aprobada).get("estado").asText()).isEqualTo("APROBADA");
        assertThat(pacienteBd(id).get("nombres")).isEqualTo("Anna");
        assertThat(filasAuditoria("APROBAR_CORRECCION_PACIENTE", solicitudId)).hasSize(1);
        assertThat(filasAuditoria("SOLICITAR_CORRECCION_PACIENTE", solicitudId)).hasSize(1);

        MvcResult otraVez = ejecutar(post("/pacientes/solicitudes-correccion/" + solicitudId + "/rechazar"), dueno, Map.of());
        assertThat(otraVez.getResponse().getStatus()).isEqualTo(409);
        JsonNode delPaciente = leer(ejecutar(get("/pacientes/" + id + "/solicitudes-correccion"), doctor, null));
        assertThat(delPaciente).hasSize(1);
    }

    @Test
    @DisplayName("Si el dueño rechaza, el paciente no cambia; una solicitud sin cambios o sin motivo se rechaza con 400")
    void rechazarYValidar() throws Exception {
        String sede = crearSede();
        UUID id = insertarPaciente(sede, "Luis", "Rojas");
        UsuarioPrueba recepcion = crearUsuario("RECEPCION", sede);
        UsuarioPrueba dueno = adminGeneral();

        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("apellidos", "Rojas Soto");
        cuerpo.put("motivo", "Falta el segundo apellido");
        String solicitudId = leer(ejecutar(post("/pacientes/" + id + "/solicitudes-correccion"), recepcion, cuerpo)).get("id").asText();

        MvcResult rechazada = ejecutar(post("/pacientes/solicitudes-correccion/" + solicitudId + "/rechazar"), dueno, Map.of("comentario", "Verificar con el DNI"));
        assertThat(rechazada.getResponse().getStatus()).isEqualTo(200);
        assertThat(leer(rechazada).get("estado").asText()).isEqualTo("RECHAZADA");
        assertThat(pacienteBd(id).get("apellidos")).isEqualTo("Rojas");

        Map<String, Object> igual = new HashMap<>();
        igual.put("nombres", "Luis");
        igual.put("motivo", "Sin cambios reales");
        assertThat(ejecutar(post("/pacientes/" + id + "/solicitudes-correccion"), recepcion, igual).getResponse().getStatus()).isEqualTo(400);

        Map<String, Object> sinMotivo = new HashMap<>();
        sinMotivo.put("nombres", "Luisa");
        assertThat(ejecutar(post("/pacientes/" + id + "/solicitudes-correccion"), recepcion, sinMotivo).getResponse().getStatus()).isEqualTo(400);
    }
}
