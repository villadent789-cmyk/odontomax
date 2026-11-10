package com.odontomax.pacientes.caracterizacion;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Caracterizacion de /antecedentes: historial inmutable, actualizacion con snapshot, reglas de gestante, validaciones,
 * permisos y el hallazgo B-049 (las alergias se separan de dos maneras distintas segun el endpoint).
 */
@Testcontainers(disabledWithoutDocker = true)
class AntecedentesCaracterizacionTest extends PacientesPostgresTestBase {

    private static final List<String> CLAVES_HISTORIAL = List.of("id", "pacienteId", "alergias", "antecedentesMedicos",
            "gestante", "semanasGestacion", "motivoCambio", "modificadoPor", "modificadoPorNombre", "creadoEn");

    private MvcResult actualizar(UsuarioPrueba u, UUID id, Map<String, Object> cuerpo) throws Exception {
        return ejecutar(put("/antecedentes/pacientes/" + id), u, cuerpo);
    }

    private MvcResult historial(UsuarioPrueba u, UUID id) throws Exception {
        return ejecutar(get("/antecedentes/pacientes/" + id + "/historial"), u);
    }

    private static Map<String, Object> cuerpo(String alergias, String antecedentes, Boolean gestante, Integer semanas, String motivo) {
        Map<String, Object> m = new HashMap<>();
        m.put("alergias", alergias);
        m.put("antecedentesMedicos", antecedentes);
        m.put("gestante", gestante);
        m.put("semanasGestacion", semanas);
        m.put("motivoCambio", motivo);
        return m;
    }

    // ------------------------------------------------------------------ historial

    @Test
    @DisplayName("Historial: vacio al inicio; luego una instantanea por actualizacion, en orden descendente, con las 10 claves y el motivo recortado")
    void historialDescendenteYSnapshot() throws Exception {
        String sede = crearSede();
        UsuarioPrueba odontologo = crearUsuario("ODONTOLOGO", sede);
        UUID id = insertarPaciente(sede, "Con", "Historial");
        vincularCita(id, odontologo);

        MvcResult vacio = historial(crearUsuario("RECEPCION", sede), id);
        assertThat(vacio.getResponse().getStatus()).isEqualTo(200);
        assertThat(leer(vacio).isArray()).isTrue();
        assertThat(leer(vacio)).isEmpty();

        assertThat(actualizar(odontologo, id, cuerpo("Polen", "Asma", true, 10, "  Primera consulta  ")).getResponse().getStatus()).isEqualTo(200);
        assertThat(actualizar(odontologo, id, cuerpo("Latex", "Diabetes", false, 12, "Segunda consulta")).getResponse().getStatus()).isEqualTo(200);

        for (String rol : List.of("ADMIN_GENERAL", "ADMIN_SEDE", "ODONTOLOGO", "RECEPCION")) {
            UsuarioPrueba u = crearUsuario(rol, "ADMIN_GENERAL".equals(rol) ? null : sede);
            if ("ODONTOLOGO".equals(rol)) vincularCita(id, u);
            MvcResult r = historial(u, id);
            assertThat(r.getResponse().getStatus()).as(rol).isEqualTo(200);
            JsonNode j = leer(r);
            assertThat(j).hasSize(2);
            for (JsonNode n : j) {
                assertThat(campos(n)).containsExactlyInAnyOrderElementsOf(CLAVES_HISTORIAL);
            }
            JsonNode nueva = j.get(0);
            JsonNode vieja = j.get(1);
            assertThat(nueva.get("motivoCambio").asText()).isEqualTo("Segunda consulta");
            assertThat(nueva.get("alergias").asText()).isEqualTo("Latex");
            assertThat(nueva.get("antecedentesMedicos").asText()).isEqualTo("Diabetes");
            assertThat(nueva.get("gestante").asBoolean()).isFalse();
            assertThat(nueva.get("semanasGestacion").asInt()).as("el snapshot guarda las semanas aunque gestante sea false").isEqualTo(12);
            assertThat(vieja.get("motivoCambio").asText()).isEqualTo("Primera consulta");
            assertThat(vieja.get("gestante").asBoolean()).isTrue();
            assertThat(vieja.get("semanasGestacion").asInt()).isEqualTo(10);
            assertThat(nueva.get("pacienteId").asText()).isEqualTo(id.toString());
            assertThat(nueva.get("modificadoPor").asText()).isEqualTo(odontologo.id().toString());
            assertThat(nueva.get("modificadoPorNombre").asText()).isEqualTo(odontologo.nombre());
            assertThat(OffsetDateTime.parse(nueva.get("creadoEn").asText())).isAfter(OffsetDateTime.parse(vieja.get("creadoEn").asText()));
        }
    }

    @Test
    @DisplayName("Historial: sede ajena 403, paciente inexistente 400 'No se encontro el paciente con ID', sin token 401")
    void historialErrores() throws Exception {
        String sedeA = crearSede();
        String sedeB = crearSede();
        UUID id = insertarPaciente(sedeA, "Historial", "Errores");
        for (String rol : List.of("ADMIN_SEDE", "ODONTOLOGO", "RECEPCION")) {
            assertThat(historial(crearUsuario(rol, sedeB), id).getResponse().getStatus()).as(rol).isEqualTo(403);
        }
        UUID inexistente = UUID.randomUUID();
        MvcResult r = historial(adminGeneral(), inexistente);
        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        assertThat(leer(r).get("message").asText()).isEqualTo("No se encontró el paciente con ID: " + inexistente);
        assertThat(historial(null, id).getResponse().getStatus()).isEqualTo(401);
    }

    // ------------------------------------------------------------------ actualizacion

    @Test
    @DisplayName("PUT: devuelve PacienteResponseDTO (29 claves) con los valores nuevos y conserva saldo, archivado, inasistencias, creado_en y consentimiento_datos_en")
    void actualizarDevuelvePacienteYConserva() throws Exception {
        String sede = crearSede();
        UsuarioPrueba admin = crearUsuario("ADMIN_SEDE", sede);
        UUID id = insertarPaciente(sede, "Antes", "Despues");
        jdbc.update("UPDATE pacientes SET saldo_pendiente = 80, archivado = true, motivo_archivo = 'm', total_inasistencias = 4, "
                + "consentimiento_datos_en = now(), telefono = '911000111', numero_historia_clinica = 'HC-9' WHERE id = ?", id);
        Map<String, Object> antes = pacienteBd(id);

        MvcResult r = actualizar(admin, id, cuerpo("Polen", "Hipertension", true, 20, "Control"));

        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        JsonNode j = leer(r);
        assertThat(campos(j)).containsExactlyInAnyOrderElementsOf(CLAVES_PACIENTE);
        assertThat(j.get("id").asText()).isEqualTo(id.toString());
        assertThat(j.get("nombreCompleto").asText()).isEqualTo("Antes Despues");
        assertThat(j.get("alergias")).hasSize(1);
        assertThat(j.get("antecedentesMedicos").asText()).isEqualTo("Hipertension");
        assertThat(j.get("gestante").asBoolean()).isTrue();
        assertThat(j.get("semanasGestacion").asInt()).isEqualTo(20);
        assertThat(j.get("saldoPendiente").decimalValue()).isEqualByComparingTo(new BigDecimal("80"));
        assertThat(j.get("archivado").asBoolean()).isTrue();
        assertThat(j.get("totalInasistencias").asInt()).isEqualTo(4);
        assertThat(j.get("telefono").asText()).isEqualTo("911000111");
        assertThat(j.get("numeroHistoriaClinica").asText()).isEqualTo("HC-9");

        Map<String, Object> despues = pacienteBd(id);
        assertThat(despues.get("consentimiento_datos_en")).as("PUT /antecedentes no pierde consentimiento_datos_en").isNotNull()
                .isEqualTo(antes.get("consentimiento_datos_en"));
        assertThat(despues.get("creado_en")).isEqualTo(antes.get("creado_en"));
        assertThat(despues.get("saldo_pendiente")).isEqualTo(antes.get("saldo_pendiente"));
        assertThat(despues.get("archivado")).isEqualTo(true);
        assertThat(despues.get("total_inasistencias")).isEqualTo(4);
        assertThat(despues.get("alergias")).isEqualTo("Polen");
        assertThat(despues.get("antecedentes_medicos")).isEqualTo("Hipertension");
        assertThat(despues.get("es_gestante")).isEqualTo(true);
        assertThat(despues.get("semanas_gestacion")).isEqualTo(20);
    }

    @Test
    @DisplayName("Alergias y antecedentes omitidos (null) SOBRESCRIBEN los valores anteriores con null")
    void camposOmitidosSobrescribenConNull() throws Exception {
        String sede = crearSede();
        UsuarioPrueba doc = crearUsuario("ODONTOLOGO", sede);
        UUID id = insertarPaciente(sede, "Sobre", "Escribe");
        vincularCita(id, doc);
        jdbc.update("UPDATE pacientes SET alergias = 'Polen', antecedentes_medicos = 'Asma' WHERE id = ?", id);

        assertThat(actualizar(doc, id, cuerpo(null, null, false, null, "Limpieza"))
                .getResponse().getStatus()).isEqualTo(200);

        assertThat(pacienteBd(id).get("alergias")).isNull();
        assertThat(pacienteBd(id).get("antecedentes_medicos")).isNull();
        assertThat(jdbc.queryForObject("SELECT alergias FROM antecedentes_historial WHERE paciente_id = ?", String.class, id)).isNull();
    }

    @Test
    @DisplayName("Regla de gestante: 'false' anula las semanas del paciente (el snapshot las conserva); 'true' las fija; B-054: omitir gestante da 500 y no guarda nada")
    void reglaDeGestante() throws Exception {
        String sede = crearSede();
        UsuarioPrueba u = crearUsuario("ODONTOLOGO", sede);
        UUID id = insertarPaciente(sede, "Regla", "Gestante");
        vincularCita(id, u);

        actualizar(u, id, cuerpo("a", "b", true, 20, "Gestante"));
        assertThat(pacienteBd(id).get("es_gestante")).isEqualTo(true);
        assertThat(pacienteBd(id).get("semanas_gestacion")).isEqualTo(20);

        // gestante=false anula las semanas aunque vengan informadas
        MvcResult falso = actualizar(u, id, cuerpo("a", "b", false, 30, "Ya no gestante"));
        assertThat(falso.getResponse().getStatus()).isEqualTo(200);
        assertThat(leer(falso).get("gestante").asBoolean()).isFalse();
        assertThat(leer(falso).get("semanasGestacion").isNull()).isTrue();
        assertThat(pacienteBd(id).get("es_gestante")).isEqualTo(false);
        assertThat(pacienteBd(id).get("semanas_gestacion")).isNull();

        // gestante=true sin semanas: semanas null
        actualizar(u, id, cuerpo("a", "b", true, null, "Gestante sin semanas"));
        assertThat(pacienteBd(id).get("es_gestante")).isEqualTo(true);
        assertThat(pacienteBd(id).get("semanas_gestacion")).isNull();

        // omitido (B-054): la instantanea (es_gestante NOT NULL) falla al insertar con null; la excepcion se traga en el
        // bloque de auditoria y la transaccion queda marcada rollback-only -> 500 y nada se guarda
        actualizar(u, id, cuerpo("a", "b", true, 25, "Fijar 25"));
        MvcResult omitido = actualizar(u, id, cuerpo("a", "b", null, 7, "Sin gestante informado"));
        int estado = omitido.getResponse().getStatus();
        assertThat(estado).as("PUT sin gestante").isEqualTo(500);
        assertThat(leer(omitido).get("message").asText()).isEqualTo("Ha ocurrido un error interno en el servidor. Por favor intente más tarde.");
        assertThat(pacienteBd(id).get("es_gestante")).isEqualTo(true);
        assertThat(pacienteBd(id).get("semanas_gestacion")).as("el 500 revierte el cambio completo").isEqualTo(25);
        assertThat(contar("SELECT count(*) FROM antecedentes_historial WHERE paciente_id = ? AND motivo_cambio = 'Sin gestante informado'", id))
                .isZero();
    }

    @Test
    @DisplayName("Validaciones: limites de 1000/3000/500 caracteres, semanas 1-45 y motivo obligatorio -> 400 con el mensaje exacto; los limites exactos se aceptan")
    void validaciones() throws Exception {
        String sede = crearSede();
        UsuarioPrueba u = crearUsuario("ODONTOLOGO", sede);
        UUID id = insertarPaciente(sede, "Valida", "Antecedentes");
        vincularCita(id, u);

        Object[][] invalidos = {
                {"alergias", "a".repeat(1001), "Las alergias no pueden superar 1000 caracteres."},
                {"antecedentesMedicos", "a".repeat(3001), "Los antecedentes médicos no pueden superar 3000 caracteres."},
                {"motivoCambio", "a".repeat(501), "El motivo no puede exceder 500 caracteres."},
                {"motivoCambio", "", "El motivo de la actualización de antecedentes es obligatorio para fines de auditoría médica."},
                {"motivoCambio", "     ", "El motivo de la actualización de antecedentes es obligatorio para fines de auditoría médica."},
                {"motivoCambio", null, "El motivo de la actualización de antecedentes es obligatorio para fines de auditoría médica."},
                {"semanasGestacion", 0, "Las semanas de gestación deben ser mínimo 1."},
                {"semanasGestacion", 46, "Las semanas de gestación deben ser máximo 45."},
        };
        for (Object[] caso : invalidos) {
            Map<String, Object> body = cuerpo("a", "b", true, 10, "Motivo valido");
            body.put((String) caso[0], caso[1]);
            MvcResult r = actualizar(u, id, body);
            assertThat(r.getResponse().getStatus()).as("%s=%s", caso[0], String.valueOf(caso[1]).length()).isEqualTo(400);
            JsonNode j = leer(r);
            assertThat(j.get("detalles").get((String) caso[0]).asText()).isEqualTo(caso[2]);
            assertThat(j.get("message").asText()).isEqualTo(caso[2]);
        }
        assertThat(contar("SELECT count(*) FROM antecedentes_historial WHERE paciente_id = ?", id)).isZero();

        Object[][] limites = {
                {"alergias", "a".repeat(1000)},
                {"antecedentesMedicos", "a".repeat(3000)},
                {"motivoCambio", "a".repeat(500)},
                {"semanasGestacion", 1},
                {"semanasGestacion", 45},
        };
        for (Object[] caso : limites) {
            Map<String, Object> body = cuerpo("a", "b", true, 10, "Motivo valido");
            body.put((String) caso[0], caso[1]);
            assertThat(actualizar(u, id, body).getResponse().getStatus()).as("%s limite", caso[0]).isEqualTo(200);
        }
        assertThat(actualizar(u, id, new HashMap<>()).getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("Permisos: RECEPCION escribe (y un cuerpo invalido da 400); sede ajena 403; inexistente 400; sin token 401; ODONTOLOGO, ADMIN_SEDE y ADMIN_GENERAL escriben")
    void permisos() throws Exception {
        String sedeA = crearSede();
        String sedeB = crearSede();
        UUID id = insertarPaciente(sedeA, "Permisos", "Antecedentes");
        Map<String, Object> valido = cuerpo("a", "b", false, null, "Motivo");

        assertThat(actualizar(crearUsuario("RECEPCION", sedeA), id, valido).getResponse().getStatus()).isEqualTo(200);
        assertThat(actualizar(crearUsuario("RECEPCION", sedeA), id, new HashMap<>()).getResponse().getStatus())
                .as("la validacion se ejecuta antes que @PreAuthorize").isEqualTo(400);
        assertThat(actualizar(null, id, valido).getResponse().getStatus()).isEqualTo(401);
        for (String rol : List.of("ODONTOLOGO", "ADMIN_SEDE")) {
            assertThat(actualizar(crearUsuario(rol, sedeB), id, valido).getResponse().getStatus()).as(rol + " ajena").isEqualTo(403);
        }
        UUID inexistente = UUID.randomUUID();
        MvcResult r = actualizar(adminGeneral(), inexistente, valido);
        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        assertThat(leer(r).get("message").asText()).isEqualTo("No se encontró el paciente con ID: " + inexistente);
        // Solo escribió recepción (los demás intentos fueron rechazados): queda un único registro
        assertThat(contar("SELECT count(*) FROM antecedentes_historial WHERE paciente_id = ?", id)).isEqualTo(1);

        for (String rol : List.of("ODONTOLOGO", "ADMIN_SEDE", "ADMIN_GENERAL")) {
            UsuarioPrueba u = crearUsuario(rol, "ADMIN_GENERAL".equals(rol) ? null : sedeA);
            if ("ODONTOLOGO".equals(rol)) vincularCita(id, u);
            assertThat(actualizar(u, id, valido).getResponse().getStatus()).as(rol).isEqualTo(200);
        }
        assertThat(contar("SELECT count(*) FROM antecedentes_historial WHERE paciente_id = ?", id)).isEqualTo(4);
    }

    @Test
    @DisplayName("El historial es inmutable (V7): UPDATE y DELETE sobre antecedentes_historial se rechazan")
    void historialInmutable() throws Exception {
        String sede = crearSede();
        UsuarioPrueba doc = crearUsuario("ODONTOLOGO", sede);
        UUID id = insertarPaciente(sede, "Inmutable", "Historial");
        vincularCita(id, doc);
        actualizar(doc, id, cuerpo("a", "b", false, null, "Alta"));
        assertThatThrownBy(() -> jdbc.update("UPDATE antecedentes_historial SET motivo_cambio = 'otro' WHERE paciente_id = ?", id))
                .hasMessageContaining("Registro inmutable");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM antecedentes_historial WHERE paciente_id = ?", id))
                .hasMessageContaining("Registro inmutable");
        assertThat(contar("SELECT count(*) FROM antecedentes_historial WHERE paciente_id = ?", id)).isEqualTo(1);
    }

    // ------------------------------------------------------------------ B-049

    @Test
    @DisplayName("B-049: 'Penicilina, Latex' se separa distinto segun el endpoint: PUT /antecedentes y exportar -> [Penicilina, Latex]; GET /pacientes (lista y page) y GET /pacientes/{id} -> [Penicilina, ' Latex']")
    void b049AlergiasSeSeparanDeDosManeras() throws Exception {
        String sede = crearSede();
        UsuarioPrueba admin = crearUsuario("ADMIN_SEDE", sede);
        UUID id = insertarPaciente(sede, "Alergico", "Doble");

        MvcResult put = actualizar(admin, id, cuerpo("Penicilina, Latex", "Ninguno", false, null, "Alergias"));
        assertThat(put.getResponse().getStatus()).isEqualTo(200);
        assertThat(pacienteBd(id).get("alergias")).isEqualTo("Penicilina, Latex");
        JsonNode deAntecedentes = leer(put).get("alergias");
        assertThat(deAntecedentes).hasSize(2);
        assertThat(deAntecedentes.get(0).asText()).isEqualTo("Penicilina");
        assertThat(deAntecedentes.get(1).asText()).isEqualTo("Latex");

        JsonNode deLista = buscarPorId(leer(ejecutar(get("/pacientes").param("sedeId", sede), admin)), id.toString()).get("alergias");
        assertThat(deLista).hasSize(2);
        assertThat(deLista.get(1).asText()).isEqualTo(" Latex");

        JsonNode dePagina = buscarPorId(leer(ejecutar(get("/pacientes").param("sedeId", sede).param("page", "0"), admin))
                .get("content"), id.toString()).get("alergias");
        assertThat(dePagina.get(1).asText()).isEqualTo(" Latex");

        JsonNode dePorId = leer(ejecutar(get("/pacientes/" + id), admin)).get("alergias");
        assertThat(dePorId.get(0).asText()).isEqualTo("Penicilina");
        assertThat(dePorId.get(1).asText()).isEqualTo(" Latex");

        JsonNode deExportar = leer(ejecutar(get("/pacientes/" + id + "/exportar"), admin)).get("datosPersonales").get("alergias");
        assertThat(deExportar).hasSize(2);
        assertThat(deExportar.get(0).asText()).isEqualTo("Penicilina");
        assertThat(deExportar.get(1).asText()).isEqualTo("Latex");

        // alergias en blanco -> lista vacia en todos
        actualizar(admin, id, cuerpo("   ", "Ninguno", false, null, "Sin alergias"));
        assertThat(leer(ejecutar(get("/pacientes/" + id), admin)).get("alergias")).isEmpty();
    }
}
