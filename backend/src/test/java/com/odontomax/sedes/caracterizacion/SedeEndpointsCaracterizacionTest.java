package com.odontomax.sedes.caracterizacion;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Caracterización de /sedes (plan de sedes, sección 8.1): contrato JSON, ids generados, normalización,
 * valores por defecto, estados y códigos de error tal como son hoy.
 */
@Testcontainers(disabledWithoutDocker = true)
class SedeEndpointsCaracterizacionTest extends SedesPostgresTestBase {

    private static final List<String> CAMPOS_SEDE = List.of("id", "nombre", "direccion", "ciudad", "telefono",
            "whatsapp", "email", "horarioTexto", "color", "colorSecundario", "sillones", "descripcion", "fotoPortada",
            "fotos", "latitud", "longitud", "activa", "unidadDeNegocio", "ruc", "montoAdelantoDefecto",
            "horasExpiracionAdelanto", "horasAnticipacionCancelacion", "horasRecordatorioWhatsapp",
            "horaInicioLlamadas", "diasVigenciaPromocionCumpleanos");

    private Map<String, Object> cuerpoSede(String nombre) {
        Map<String, Object> m = new HashMap<>();
        m.put("nombre", nombre);
        m.put("direccion", "Av. Prueba 123");
        m.put("sillones", 2);
        return m;
    }

    private JsonNode crearSede(UsuarioPrueba admin, Map<String, Object> cuerpo) throws Exception {
        MvcResult r = mockMvc.perform(post("/sedes").header("Authorization", admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(json(cuerpo))).andReturn();
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(201);
        return leer(r);
    }

    @Test
    @DisplayName("GET /sedes anónimo: 200, solo activas, orden por nombre, 17 claves exactas y fotos como array")
    void listarActivasAnonimo() throws Exception {
        String t = tag();
        insertarSede("sede-act-" + t, "AAA Activa " + t, true, 1);
        insertarSede("sede-inact-" + t, "AAA Inactiva " + t, false, 1);

        MvcResult r = mockMvc.perform(get("/sedes")).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        JsonNode lista = leer(r);

        List<String> esperadas = jdbc.queryForList("SELECT id FROM sedes WHERE activa ORDER BY nombre", String.class);
        assertThat(ids(lista)).containsExactlyElementsOf(esperadas)
                .contains("sede-act-" + t, SEDE_CHILCA_1, SEDE_ELTAMBO)
                .doesNotContain("sede-inact-" + t);
        lista.forEach(n -> assertThat(nombresDeCampos(n)).containsExactlyInAnyOrderElementsOf(CAMPOS_SEDE));

        JsonNode nueva = buscarPorId(lista, "sede-act-" + t);
        assertThat(nueva.get("fotos").isArray()).isTrue();
        assertThat(nueva.get("fotos")).isEmpty();
        assertThat(nueva.get("activa").asBoolean()).isTrue();
        assertThat(nueva.get("fotoPortada").isNull()).isTrue();
        assertThat(nueva.get("latitud").isNull()).isTrue();

        JsonNode chilca = buscarPorId(lista, SEDE_CHILCA_1);
        assertThat(chilca.get("fotos")).hasSize(4);
        assertThat(chilca.get("fotos").get(0).asText()).isEqualTo("/sedes/chilca-1/fachada.jpg");
        assertThat(chilca.get("sillones").asInt()).isEqualTo(4);
        assertThat(chilca.get("ciudad").asText()).isEqualTo("Huancayo - Chilca");
    }

    @Test
    @DisplayName("GET /sedes/admin (ADMIN_GENERAL): incluye inactivas, orden por nombre; sin token 401, RECEPCION 403")
    void listarTodasAdmin() throws Exception {
        String t = tag();
        insertarSede("sede-inact-" + t, "AAA Inactiva " + t, false, 1);
        UsuarioPrueba admin = adminGeneral();

        MvcResult r = mockMvc.perform(get("/sedes/admin").header("Authorization", admin.bearer())).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        JsonNode lista = leer(r);
        assertThat(ids(lista)).containsExactlyElementsOf(
                jdbc.queryForList("SELECT id FROM sedes ORDER BY nombre", String.class));
        assertThat(buscarPorId(lista, "sede-inact-" + t).get("activa").asBoolean()).isFalse();
        lista.forEach(n -> assertThat(nombresDeCampos(n)).containsExactlyInAnyOrderElementsOf(CAMPOS_SEDE));

        assertThat(mockMvc.perform(get("/sedes/admin")).andReturn().getResponse().getStatus()).isEqualTo(401);
        UsuarioPrueba recepcion = crearUsuario("RECEPCION", SEDE_CHILCA_1);
        assertThat(mockMvc.perform(get("/sedes/admin").header("Authorization", recepcion.bearer()))
                .andReturn().getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("POST /sedes: 201, id 'sede-'+slug, recorte de espacios, defaults de ciudad/colores, opcionales en blanco a null")
    void crearConDefaults() throws Exception {
        String t = tag();
        UsuarioPrueba admin = adminGeneral();
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("nombre", "  Clínica Ñandú & Sonrisa #1 " + t + "  ");
        cuerpo.put("direccion", "  Jr. Real 100  ");
        cuerpo.put("ciudad", "   ");
        cuerpo.put("telefono", "   ");
        cuerpo.put("email", "");
        cuerpo.put("color", "  ");
        cuerpo.put("colorSecundario", null);
        cuerpo.put("horarioTexto", "  Lun - Vie  ");
        cuerpo.put("sillones", 2);
        cuerpo.put("descripcion", "  Desc  ");

        JsonNode s = crearSede(admin, cuerpo);
        String id = "sede-clinica-nandu-sonrisa-1-" + t;
        assertThat(nombresDeCampos(s)).containsExactlyInAnyOrderElementsOf(CAMPOS_SEDE);
        assertThat(s.get("id").asText()).isEqualTo(id);
        assertThat(s.get("nombre").asText()).isEqualTo("Clínica Ñandú & Sonrisa #1 " + t);
        assertThat(s.get("direccion").asText()).isEqualTo("Jr. Real 100");
        assertThat(s.get("ciudad").asText()).isEqualTo("Huancayo");
        assertThat(s.get("color").asText()).isEqualTo("#C59B27");
        assertThat(s.get("colorSecundario").asText()).isEqualTo("#FEF3C7");
        assertThat(s.get("telefono").isNull()).isTrue();
        assertThat(s.get("whatsapp").isNull()).isTrue();
        assertThat(s.get("email").isNull()).isTrue();
        assertThat(s.get("horarioTexto").asText()).isEqualTo("Lun - Vie");
        assertThat(s.get("descripcion").asText()).isEqualTo("Desc");
        assertThat(s.get("sillones").asInt()).isEqualTo(2);
        assertThat(s.get("fotoPortada").isNull()).isTrue();
        assertThat(s.get("fotos").isArray()).isTrue();
        assertThat(s.get("fotos")).isEmpty();
        assertThat(s.get("latitud").isNull()).isTrue();
        assertThat(s.get("longitud").isNull()).isTrue();
        assertThat(s.get("activa").asBoolean()).isTrue();

        Map<String, Object> fila = jdbc.queryForMap("SELECT nombre, ciudad, color, color_secundario, telefono, email, "
                + "activa, array_length(fotos, 1) AS nfotos FROM sedes WHERE id = ?", id);
        assertThat(fila.get("nombre")).isEqualTo("Clínica Ñandú & Sonrisa #1 " + t);
        assertThat(fila.get("ciudad")).isEqualTo("Huancayo");
        assertThat(fila.get("color")).isEqualTo("#C59B27");
        assertThat(fila.get("color_secundario")).isEqualTo("#FEF3C7");
        assertThat(fila.get("telefono")).isNull();
        assertThat(fila.get("email")).isNull();
        assertThat(fila.get("activa")).isEqualTo(true);
        assertThat(fila.get("nfotos")).isNull(); // array vacío '{}'
    }

    @Test
    @DisplayName("POST /sedes con ciudad y colores informados: se guardan recortados")
    void crearConCiudadYColores() throws Exception {
        String t = tag();
        Map<String, Object> cuerpo = cuerpoSede("Sede Colores " + t);
        cuerpo.put("ciudad", "  Lima  ");
        cuerpo.put("color", " #111111 ");
        cuerpo.put("colorSecundario", " #222222 ");
        cuerpo.put("whatsapp", " 51999 ");
        cuerpo.put("email", "sede@odontomax.pe");
        JsonNode s = crearSede(adminGeneral(), cuerpo);
        assertThat(s.get("ciudad").asText()).isEqualTo("Lima");
        assertThat(s.get("color").asText()).isEqualTo("#111111");
        assertThat(s.get("colorSecundario").asText()).isEqualTo("#222222");
        assertThat(s.get("whatsapp").asText()).isEqualTo("51999");
        assertThat(s.get("email").asText()).isEqualTo("sede@odontomax.pe");
    }

    @Test
    @DisplayName("POST /sedes: nombre repetido recibe sufijo -2, -3")
    void crearSufijos() throws Exception {
        String t = tag();
        UsuarioPrueba admin = adminGeneral();
        assertThat(crearSede(admin, cuerpoSede("Sede Repetida " + t)).get("id").asText()).isEqualTo("sede-sede-repetida-" + t);
        assertThat(crearSede(admin, cuerpoSede("Sede Repetida " + t)).get("id").asText()).isEqualTo("sede-sede-repetida-" + t + "-2");
        assertThat(crearSede(admin, cuerpoSede("SEDE  repetida! " + t)).get("id").asText()).isEqualTo("sede-sede-repetida-" + t + "-3");
    }

    @Test
    @DisplayName("POST /sedes: el slug se recorta a 40 caracteres y sin guion final")
    void crearRecorteA40() throws Exception {
        String t = tag();
        String a33 = "a".repeat(33);
        // slug completo: t(6) + '-' + 33 'a' = 40 caracteres justos, luego '-bbbbbbbbbb'
        JsonNode s = crearSede(adminGeneral(), cuerpoSede(t + " " + a33 + " bbbbbbbbbb"));
        assertThat(s.get("id").asText()).isEqualTo("sede-" + t + "-" + a33);

        String t2 = tag();
        String a32 = "a".repeat(32);
        // slug: t2(6) + '-' + 32 'a' + '-' = 40, el guion final se elimina
        JsonNode s2 = crearSede(adminGeneral(), cuerpoSede(t2 + " " + a32 + " bbbbbbbbbb"));
        assertThat(s2.get("id").asText()).isEqualTo("sede-" + t2 + "-" + a32);
    }

    @Test
    @DisplayName("POST /sedes: nombre solo con símbolos → id 'sede-nueva' (o con sufijo si ya existe)")
    void crearSoloSimbolos() throws Exception {
        boolean yaExistia = existeSede("sede-nueva");
        JsonNode s = crearSede(adminGeneral(), cuerpoSede("¡¡ ### ¿¿"));
        if (yaExistia) {
            assertThat(s.get("id").asText()).matches("sede-nueva-\\d+");
        } else {
            assertThat(s.get("id").asText()).isEqualTo("sede-nueva");
        }
        assertThat(s.get("nombre").asText()).isEqualTo("¡¡ ### ¿¿");
    }

    @Test
    @DisplayName("PUT /sedes/{id}: cambia los editables, conserva id, ciudad/colores en blanco, fotos, coordenadas y estado; borra opcionales omitidos")
    void actualizar() throws Exception {
        String t = tag();
        UsuarioPrueba admin = adminGeneral();
        Map<String, Object> alta = cuerpoSede("Sede Editar " + t);
        alta.put("ciudad", "Lima");
        alta.put("color", "#111111");
        alta.put("colorSecundario", "#222222");
        alta.put("email", "antes@odontomax.pe");
        alta.put("telefono", "111");
        String id = crearSede(admin, alta).get("id").asText();
        jdbc.update("UPDATE sedes SET fotos = ARRAY['/a.jpg','/b.jpg']::text[], foto_portada = '/a.jpg', "
                + "latitud = -12.065100, longitud = -75.204900, activa = false WHERE id = ?", id);

        Map<String, Object> cambio = new HashMap<>();
        cambio.put("nombre", " Sede Editada " + t + " ");
        cambio.put("direccion", "Nueva dirección 9");
        cambio.put("sillones", 5);
        cambio.put("telefono", "222");
        cambio.put("ciudad", " ");
        cambio.put("color", "");
        // colorSecundario y email omitidos
        MvcResult r = mockMvc.perform(put("/sedes/" + id).header("Authorization", admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(json(cambio))).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        JsonNode s = leer(r);
        assertThat(nombresDeCampos(s)).containsExactlyInAnyOrderElementsOf(CAMPOS_SEDE);
        assertThat(s.get("id").asText()).isEqualTo(id);
        assertThat(s.get("nombre").asText()).isEqualTo("Sede Editada " + t);
        assertThat(s.get("direccion").asText()).isEqualTo("Nueva dirección 9");
        assertThat(s.get("sillones").asInt()).isEqualTo(5);
        assertThat(s.get("telefono").asText()).isEqualTo("222");
        assertThat(s.get("email").isNull()).isTrue(); // omitido → se borra
        assertThat(s.get("ciudad").asText()).isEqualTo("Lima");
        assertThat(s.get("color").asText()).isEqualTo("#111111");
        assertThat(s.get("colorSecundario").asText()).isEqualTo("#222222");
        assertThat(s.get("fotos").get(0).asText()).isEqualTo("/a.jpg");
        assertThat(s.get("fotos")).hasSize(2);
        assertThat(s.get("fotoPortada").asText()).isEqualTo("/a.jpg");
        assertThat(s.get("latitud").decimalValue()).isEqualByComparingTo(new BigDecimal("-12.0651"));
        assertThat(s.get("longitud").decimalValue()).isEqualByComparingTo(new BigDecimal("-75.2049"));
        assertThat(s.get("activa").asBoolean()).isFalse();

        Map<String, Object> fila = jdbc.queryForMap("SELECT nombre, email, ciudad, activa FROM sedes WHERE id = ?", id);
        assertThat(fila.get("nombre")).isEqualTo("Sede Editada " + t);
        assertThat(fila.get("email")).isNull();
        assertThat(fila.get("ciudad")).isEqualTo("Lima");
        assertThat(fila.get("activa")).isEqualTo(false);
    }

    @Test
    @DisplayName("PUT /sedes/{id} inexistente: 404 con status/error/message/mensaje")
    void actualizarInexistente404() throws Exception {
        String id = "sede-no-existe-" + tag();
        MvcResult r = mockMvc.perform(put("/sedes/" + id).header("Authorization", adminGeneral().bearer())
                .contentType(MediaType.APPLICATION_JSON).content(json(cuerpoSede("Lo que sea")))).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(404);
        JsonNode b = leer(r);
        assertThat(b.get("status").asInt()).isEqualTo(404);
        assertThat(b.get("error").asText()).isEqualTo("Not Found");
        assertThat(b.get("message").asText()).isEqualTo("Sede no encontrada: " + id);
        assertThat(b.get("mensaje").asText()).isEqualTo("Sede no encontrada: " + id);
        assertThat(nombresDeCampos(b)).containsExactlyInAnyOrder("timestamp", "status", "error", "message", "mensaje");
        assertThat(existeSede(id)).isFalse();
    }

    @Test
    @DisplayName("PATCH /sedes/{id}/estado: desactivar la saca de GET /sedes y la deja en /admin; reactivar la devuelve")
    void cambiarEstado() throws Exception {
        UsuarioPrueba admin = adminGeneral();
        String id = crearSede(admin, cuerpoSede("Sede Estado " + tag())).get("id").asText();

        MvcResult r = mockMvc.perform(patch("/sedes/" + id + "/estado").param("activa", "false")
                .header("Authorization", admin.bearer())).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        assertThat(leer(r).get("activa").asBoolean()).isFalse();
        assertThat(nombresDeCampos(leer(r))).containsExactlyInAnyOrderElementsOf(CAMPOS_SEDE);
        assertThat(ids(leer(mockMvc.perform(get("/sedes")).andReturn()))).doesNotContain(id);
        assertThat(ids(leer(mockMvc.perform(get("/sedes/admin").header("Authorization", admin.bearer())).andReturn())))
                .contains(id);

        r = mockMvc.perform(patch("/sedes/" + id + "/estado").param("activa", "true")
                .header("Authorization", admin.bearer())).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        assertThat(leer(r).get("activa").asBoolean()).isTrue();
        assertThat(ids(leer(mockMvc.perform(get("/sedes")).andReturn()))).contains(id);
    }

    @Test
    @DisplayName("PATCH /sedes/{id}/estado: inexistente 404; sin 'activa' 400 'Parámetro obligatorio faltante: activa'")
    void cambiarEstadoErrores() throws Exception {
        UsuarioPrueba admin = adminGeneral();
        String id = "sede-no-existe-" + tag();
        MvcResult r = mockMvc.perform(patch("/sedes/" + id + "/estado").param("activa", "false")
                .header("Authorization", admin.bearer())).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(404);
        assertThat(leer(r).get("message").asText()).isEqualTo("Sede no encontrada: " + id);
        assertThat(leer(r).get("error").asText()).isEqualTo("Not Found");

        r = mockMvc.perform(patch("/sedes/" + SEDE_CHILCA_1 + "/estado").header("Authorization", admin.bearer())).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        assertThat(leer(r).get("status").asInt()).isEqualTo(400);
        assertThat(leer(r).get("error").asText()).isEqualTo("Bad Request");
        assertThat(leer(r).get("message").asText()).isEqualTo("Parámetro obligatorio faltante: activa");
        assertThat(leer(r).get("mensaje").asText()).isEqualTo("Parámetro obligatorio faltante: activa");
    }

    @Test
    @DisplayName("Validación de POST /sedes: sillones 0, 100 y nulo, email inválido y nombre en blanco → 400 con 'detalles'; no se crea nada")
    void validacion() throws Exception {
        UsuarioPrueba admin = adminGeneral();
        Integer antes = jdbc.queryForObject("SELECT count(*) FROM sedes", Integer.class);

        Object[][] casos = {
                {"sillones", 0, "La sede debe tener al menos 1 sillón"},
                {"sillones", 100, "La cantidad de sillones no puede superar 99"},
                {"sillones", null, "La cantidad de sillones es obligatoria"},
                {"email", "no-es-un-correo", "El correo de la sede no tiene un formato válido"},
                {"nombre", "   ", "El nombre de la sede es obligatorio"},
                {"direccion", null, "La dirección de la sede es obligatoria"},
                {"telefono", "1".repeat(31), "El teléfono no puede superar los 30 caracteres"},
        };
        for (Object[] caso : casos) {
            Map<String, Object> cuerpo = cuerpoSede("Sede Invalida " + tag());
            cuerpo.put((String) caso[0], caso[1]);
            MvcResult r = mockMvc.perform(post("/sedes").header("Authorization", admin.bearer())
                    .contentType(MediaType.APPLICATION_JSON).content(json(cuerpo))).andReturn();
            assertThat(r.getResponse().getStatus()).as("caso %s=%s", caso[0], caso[1]).isEqualTo(400);
            JsonNode b = leer(r);
            assertThat(b.get("status").asInt()).isEqualTo(400);
            assertThat(b.get("error").asText()).isEqualTo("Validación fallida");
            assertThat(b.get("detalles").get((String) caso[0]).asText()).isEqualTo(caso[2]);
            assertThat(nombresDeCampos(b.get("detalles"))).containsExactly((String) caso[0]);
            assertThat(b.get("message").asText()).isEqualTo(caso[2]);
            assertThat(b.get("mensaje").asText()).isEqualTo(caso[2]);
        }
        // sillones 1 y 99 son válidos
        Map<String, Object> uno = cuerpoSede("Sede Limite Uno " + tag());
        uno.put("sillones", 1);
        assertThat(crearSede(admin, uno).get("sillones").asInt()).isEqualTo(1);
        Map<String, Object> nn = cuerpoSede("Sede Limite NN " + tag());
        nn.put("sillones", 99);
        assertThat(crearSede(admin, nn).get("sillones").asInt()).isEqualTo(99);

        Integer despues = jdbc.queryForObject("SELECT count(*) FROM sedes", Integer.class);
        assertThat(despues).isEqualTo(antes + 2);
    }

    @Test
    @DisplayName("B-027: errores de formato (parámetro con tipo inválido, JSON mal formado o con tipos erróneos) → 400 con el formato estándar y sin detalles internos; email solo con espacios → 400")
    void erroresDeFormato() throws Exception {
        UsuarioPrueba admin = adminGeneral();
        List<String> claves = List.of("timestamp", "status", "error", "message", "mensaje");
        MvcResult r = mockMvc.perform(patch("/sedes/" + SEDE_CHILCA_1 + "/estado").param("activa", "quizas")
                .header("Authorization", admin.bearer())).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        assertThat(leer(r).get("status").asInt()).isEqualTo(400);
        assertThat(leer(r).get("error").asText()).isEqualTo("Bad Request");
        assertThat(leer(r).get("message").asText()).isEqualTo("El parámetro 'activa' tiene un valor inválido.");
        assertThat(leer(r).get("mensaje").asText()).isEqualTo("El parámetro 'activa' tiene un valor inválido.");
        assertThat(nombresDeCampos(leer(r))).containsExactlyInAnyOrderElementsOf(claves);

        String jsonInvalido = "El cuerpo de la solicitud no es un JSON válido o tiene un formato incorrecto.";
        r = mockMvc.perform(post("/sedes").header("Authorization", admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\": ")).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        assertThat(leer(r).get("error").asText()).isEqualTo("Bad Request");
        assertThat(leer(r).get("message").asText()).isEqualTo(jsonInvalido);
        assertThat(nombresDeCampos(leer(r))).containsExactlyInAnyOrderElementsOf(claves);
        assertThat(jdbc.queryForObject("SELECT activa FROM sedes WHERE id = ?", Boolean.class, SEDE_CHILCA_1)).isTrue();

        // Un tipo erróneo dentro del JSON: mismo 400, sin nombres de clases ni de Jackson
        r = mockMvc.perform(post("/sedes").header("Authorization", admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\": \"X\", \"direccion\": \"Y\", \"sillones\": \"muchos\"}"))
                .andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        assertThat(leer(r).get("message").asText()).isEqualTo(jsonInvalido);
        assertThat(r.getResponse().getContentAsString()).doesNotContain("Integer", "jackson", "Exception");

        // Es global: también un id con formato inválido en la ruta de otro módulo
        r = mockMvc.perform(get("/especialistas/no-es-un-uuid/horarios").header("Authorization", admin.bearer()))
                .andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        assertThat(leer(r).get("message").asText()).isEqualTo("El parámetro 'id' tiene un valor inválido.");

        // A diferencia de "" (válido, se guarda null), un email solo con espacios no pasa @Email
        Map<String, Object> emailEspacios = cuerpoSede("Sede Email Espacios " + tag());
        emailEspacios.put("email", "   ");
        r = mockMvc.perform(post("/sedes").header("Authorization", admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(json(emailEspacios))).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        assertThat(leer(r).get("detalles").get("email").asText()).isEqualTo("El correo de la sede no tiene un formato válido");
    }

    @Test
    @DisplayName("Mutaciones de /sedes: sin token 401, ADMIN_SEDE 403 (cadena de filtros real)")
    void seguridadMutaciones() throws Exception {
        assertThat(mockMvc.perform(post("/sedes").contentType(MediaType.APPLICATION_JSON)
                .content(json(cuerpoSede("X " + tag())))).andReturn().getResponse().getStatus()).isEqualTo(401);
        UsuarioPrueba adminSede = crearUsuario("ADMIN_SEDE", SEDE_CHILCA_1);
        assertThat(mockMvc.perform(patch("/sedes/" + SEDE_CHILCA_1 + "/estado").param("activa", "false")
                .header("Authorization", adminSede.bearer())).andReturn().getResponse().getStatus()).isEqualTo(403);
        assertThat(jdbc.queryForObject("SELECT activa FROM sedes WHERE id = ?", Boolean.class, SEDE_CHILCA_1)).isTrue();
    }
}
