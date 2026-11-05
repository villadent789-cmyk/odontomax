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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Eliminar una radiografía o un paciente exige la autorización del dueño: el resto de roles lo solicita con su motivo y
 * el dueño lo aprueba (se elimina de verdad) o lo rechaza (no cambia nada).
 */
@Testcontainers(disabledWithoutDocker = true)
class SolicitudEliminacionPostgresIntegrationTest extends PacientesPostgresTestBase {

    private Map<String, Object> cuerpo(String tipo, UUID objetivo, String motivo) {
        Map<String, Object> m = new HashMap<>();
        m.put("tipo", tipo);
        m.put("objetivoId", objetivo.toString());
        m.put("motivo", motivo);
        return m;
    }

    private UUID insertarRadiografia(UUID pacienteId, String sedeId) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO radiografias_estudios (id, paciente_id, sede_id, tipo_estudio, etapa, archivo_url, nombre_archivo, tamano_bytes) "
                + "VALUES (?, ?, ?, 'PANORAMICA', 'INICIAL', ?, 'foto-equivocada.jpg', 1000)",
                id, pacienteId, sedeId, "pacientes/" + pacienteId + "/rx_prueba.jpg");
        return id;
    }

    @Test
    @DisplayName("El admin de sede ya no elimina radiografías directamente: solo el dueño (403 para el resto)")
    void eliminacionDirectaSoloDelDueno() throws Exception {
        String sede = crearSede();
        UUID paciente = insertarPaciente(sede, "Ana", "Perez");
        UUID rx = insertarRadiografia(paciente, sede);
        UsuarioPrueba adminSede = crearUsuario("ADMIN_SEDE", sede);

        assertThat(ejecutar(delete("/radiografias/" + rx + "?motivo=Foto equivocada"), adminSede, null).getResponse().getStatus()).isEqualTo(403);
        assertThat(contar("SELECT count(*) FROM radiografias_estudios WHERE id = ?", rx)).isEqualTo(1);
    }

    @Test
    @DisplayName("Radiografía: solicitar → el dueño aprueba → se elimina y la solicitud queda como constancia")
    void solicitarYAprobarRadiografia() throws Exception {
        String sede = crearSede();
        UUID paciente = insertarPaciente(sede, "Ana", "Perez");
        UUID rx = insertarRadiografia(paciente, sede);
        UsuarioPrueba doctor = crearUsuario("ODONTOLOGO", sede);
        vincularCita(paciente, doctor);
        UsuarioPrueba dueno = adminGeneral();

        MvcResult creada = ejecutar(post("/solicitudes-eliminacion"), doctor, cuerpo("RADIOGRAFIA", rx, "Subí la foto de otro paciente"));
        assertThat(creada.getResponse().getStatus()).isEqualTo(201);
        JsonNode s = leer(creada);
        assertThat(s.get("estado").asText()).isEqualTo("PENDIENTE");
        assertThat(s.get("descripcion").asText()).contains("PANORAMICA").contains("foto-equivocada.jpg");
        String solicitudId = s.get("id").asText();
        assertThat(contar("SELECT count(*) FROM radiografias_estudios WHERE id = ?", rx)).isEqualTo(1);

        // no se puede pedir dos veces lo mismo mientras esté pendiente
        assertThat(ejecutar(post("/solicitudes-eliminacion"), doctor, cuerpo("RADIOGRAFIA", rx, "Otra vez lo mismo")).getResponse().getStatus()).isEqualTo(409);
        // el doctor no puede resolverla; el dueño la ve pendiente
        assertThat(ejecutar(post("/solicitudes-eliminacion/" + solicitudId + "/aprobar"), doctor, Map.of()).getResponse().getStatus()).isEqualTo(403);
        assertThat(leer(ejecutar(get("/solicitudes-eliminacion/pendientes"), dueno, null)).findValuesAsText("id")).contains(solicitudId);

        MvcResult aprobada = ejecutar(post("/solicitudes-eliminacion/" + solicitudId + "/aprobar"), dueno, Map.of("comentario", "Autorizado"));
        assertThat(aprobada.getResponse().getStatus()).isEqualTo(200);
        assertThat(leer(aprobada).get("estado").asText()).isEqualTo("APROBADA");
        assertThat(contar("SELECT count(*) FROM radiografias_estudios WHERE id = ?", rx)).isZero();
        assertThat(filasAuditoria("APROBAR_ELIMINACION", solicitudId)).hasSize(1);
        assertThat(filasAuditoria("SOLICITAR_ELIMINACION", solicitudId)).hasSize(1);

        assertThat(ejecutar(post("/solicitudes-eliminacion/" + solicitudId + "/rechazar"), dueno, Map.of()).getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    @DisplayName("Paciente: recepción solicita, el dueño aprueba y el paciente desaparece; con historial no se puede y todo queda pendiente")
    void solicitarYAprobarPaciente() throws Exception {
        String sede = crearSede();
        UsuarioPrueba recepcion = crearUsuario("RECEPCION", sede);
        UsuarioPrueba dueno = adminGeneral();
        UUID duplicado = insertarPaciente(sede, "Luis", "Rojas");

        MvcResult creada = ejecutar(post("/solicitudes-eliminacion"), recepcion, cuerpo("PACIENTE", duplicado, "Lo registré dos veces"));
        assertThat(creada.getResponse().getStatus()).isEqualTo(201);
        String solicitudId = leer(creada).get("id").asText();
        assertThat(existePaciente(duplicado)).isTrue();

        assertThat(ejecutar(post("/solicitudes-eliminacion/" + solicitudId + "/aprobar"), dueno, Map.of()).getResponse().getStatus()).isEqualTo(200);
        assertThat(existePaciente(duplicado)).isFalse();

        // un paciente con historial clínico no se elimina: la aprobación falla, se revierte y la solicitud sigue pendiente
        UUID conHistorial = insertarPaciente(sede, "Marta", "Soto");
        insertarPago(conHistorial, sede, "50.00", null);
        String otra = leer(ejecutar(post("/solicitudes-eliminacion"), recepcion, cuerpo("PACIENTE", conHistorial, "Lo registré por error"))).get("id").asText();
        assertThat(ejecutar(post("/solicitudes-eliminacion/" + otra + "/aprobar"), dueno, Map.of()).getResponse().getStatus()).isEqualTo(409);
        assertThat(existePaciente(conHistorial)).isTrue();
        assertThat(contar("SELECT count(*) FROM solicitudes_eliminacion WHERE id = ?::uuid AND estado = 'PENDIENTE'", otra)).isEqualTo(1);
    }

    @Test
    @DisplayName("Si el dueño rechaza no se elimina nada; sin motivo válido o con un objetivo inexistente es 400/404")
    void rechazarYValidar() throws Exception {
        String sede = crearSede();
        UUID paciente = insertarPaciente(sede, "Eva", "Luna");
        UUID rx = insertarRadiografia(paciente, sede);
        UsuarioPrueba recepcion = crearUsuario("RECEPCION", sede);
        UsuarioPrueba dueno = adminGeneral();

        String id = leer(ejecutar(post("/solicitudes-eliminacion"), recepcion, cuerpo("RADIOGRAFIA", rx, "Está duplicada"))).get("id").asText();
        MvcResult rechazada = ejecutar(post("/solicitudes-eliminacion/" + id + "/rechazar"), dueno, Map.of("comentario", "Se necesita para el seguimiento"));
        assertThat(rechazada.getResponse().getStatus()).isEqualTo(200);
        assertThat(leer(rechazada).get("estado").asText()).isEqualTo("RECHAZADA");
        assertThat(contar("SELECT count(*) FROM radiografias_estudios WHERE id = ?", rx)).isEqualTo(1);

        assertThat(ejecutar(post("/solicitudes-eliminacion"), recepcion, cuerpo("RADIOGRAFIA", rx, "no")).getResponse().getStatus()).isEqualTo(400);
        assertThat(ejecutar(post("/solicitudes-eliminacion"), recepcion, cuerpo("TRATAMIENTO", rx, "Motivo válido")).getResponse().getStatus()).isEqualTo(400);
        int inexistente = ejecutar(post("/solicitudes-eliminacion"), recepcion, cuerpo("PACIENTE", UUID.randomUUID(), "Motivo válido")).getResponse().getStatus();
        assertThat(inexistente).isIn(400, 404);
    }
}
