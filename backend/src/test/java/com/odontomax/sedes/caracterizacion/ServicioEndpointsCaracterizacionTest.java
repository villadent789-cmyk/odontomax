package com.odontomax.sedes.caracterizacion;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Caracterización de /servicios y del catálogo público (plan de sedes, sección 8.2): contrato JSON, aislamiento
 * por sede del listado, ids, normalización y errores (400 para id inexistente, a diferencia del 404 de sedes).
 */
@Testcontainers(disabledWithoutDocker = true)
class ServicioEndpointsCaracterizacionTest extends SedesPostgresTestBase {

    private static final List<String> CAMPOS_SERVICIO = List.of("id", "nombre", "especialidad", "descripcion",
            "duracionMin", "precio", "activo", "orden", "comisionPorcentaje", "comisionFija", "precioLeve", "precioModerado", "precioSevero");

    private Map<String, Object> cuerpoServicio(String nombre) {
        Map<String, Object> m = new HashMap<>();
        m.put("nombre", nombre);
        m.put("especialidad", "Odontología General");
        m.put("duracionMin", 30);
        return m;
    }

    private JsonNode crearServicio(UsuarioPrueba admin, Map<String, Object> cuerpo) throws Exception {
        MvcResult r = mockMvc.perform(post("/servicios").header("Authorization", admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(json(cuerpo))).andReturn();
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(201);
        return leer(r);
    }

    private MvcResult listar(UsuarioPrueba u, String sedeId) throws Exception {
        var b = get("/servicios").header("Authorization", u.bearer());
        if (sedeId != null) {
            b = b.param("sedeId", sedeId);
        }
        return mockMvc.perform(b).andReturn();
    }

    private static List<Integer> ordenes(JsonNode lista) {
        List<Integer> o = new ArrayList<>();
        lista.forEach(n -> o.add(n.get("orden").asInt()));
        return o;
    }

    private List<String> activosEnBd() {
        return jdbc.queryForList("SELECT id FROM servicios_web WHERE activo AND unidad_de_negocio = 'VILLADENT'", String.class);
    }

    /** La reserva web solo ofrece los servicios marcados como visibles (V52: el tarifario detallado no lo está). */
    private List<String> visiblesEnReservaEnBd() {
        return jdbc.queryForList("SELECT id FROM servicios_web WHERE activo AND visible_en_reserva AND unidad_de_negocio = 'VILLADENT'", String.class);
    }

    @Test
    @DisplayName("GET /servicios: solo activos, orden por 'orden', 8 claves; la lista no depende del sedeId")
    void listarNoFiltraPorSede() throws Exception {
        UsuarioPrueba admin = adminGeneral();
        Map<String, Object> inactivo = cuerpoServicio("Servicio Inactivo " + tag());
        String idInactivo = crearServicio(admin, inactivo).get("id").asText();
        jdbc.update("UPDATE servicios_web SET activo = false WHERE id = ?", idInactivo);

        JsonNode sinSede = leer(listar(admin, null));
        JsonNode chilca = leer(listar(admin, SEDE_CHILCA_1));
        JsonNode tambo = leer(listar(admin, SEDE_ELTAMBO));

        assertThat(ids(sinSede)).containsExactlyInAnyOrderElementsOf(activosEnBd()).doesNotContain(idInactivo)
                .contains("tar-profilaxis").doesNotContain("srv-eval-odon", "srv-1");
        assertThat(ids(chilca)).containsExactlyInAnyOrderElementsOf(ids(sinSede));
        assertThat(ids(tambo)).containsExactlyInAnyOrderElementsOf(ids(sinSede));
        assertThat(ordenes(sinSede)).isSorted();
        sinSede.forEach(n -> assertThat(nombresDeCampos(n)).containsExactlyInAnyOrderElementsOf(CAMPOS_SERVICIO));

        // Un servicio del tarifario del cliente (V52): su precio es el «desde» del tarifario.
        JsonNode profilaxis = buscarPorId(sinSede, "tar-profilaxis");
        assertThat(profilaxis.get("nombre").asText()).isEqualTo("Profilaxis");
        assertThat(profilaxis.get("precio").decimalValue()).isEqualByComparingTo(new BigDecimal("70.00"));
        assertThat(profilaxis.get("duracionMin").asInt()).isEqualTo(40);
        assertThat(profilaxis.get("activo").asBoolean()).isTrue();
        assertThat(profilaxis.get("orden").asInt()).isEqualTo(131);
    }

    @Test
    @DisplayName("B-021: GET /servicios es un catálogo global: el sedeId se ignora (propia, ajena, inexistente, blanco o mayúsculas → 200, misma lista) para todos los roles")
    void sedeIdIgnorado() throws Exception {
        UsuarioPrueba admin = adminGeneral();
        List<String> esperado = ids(leer(listar(admin, null)));
        for (String rol : List.of("ADMIN_SEDE", "RECEPCION", "ODONTOLOGO")) {
            UsuarioPrueba u = crearUsuario(rol, SEDE_CHILCA_1);
            for (String sedeId : new String[]{SEDE_ELTAMBO, "sede-inexistente-" + tag(), SEDE_CHILCA_1, null, "   ",
                    "SEDE-CHILCA-1"}) {
                MvcResult r = listar(u, sedeId);
                assertThat(r.getResponse().getStatus()).as(rol + " sedeId=" + sedeId).isEqualTo(200);
                assertThat(ids(leer(r))).as(rol + " sedeId=" + sedeId).containsExactlyInAnyOrderElementsOf(esperado);
            }
        }

        assertThat(listar(admin, SEDE_ELTAMBO).getResponse().getStatus()).isEqualTo(200);
        assertThat(listar(admin, "sede-inexistente-" + tag()).getResponse().getStatus()).isEqualTo(200);
        assertThat(listar(admin, null).getResponse().getStatus()).isEqualTo(200);

        assertThat(mockMvc.perform(get("/servicios")).andReturn().getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("B-021: GET /servicios: un ODONTOLOGO sin sede asignada recibe 200 y el mismo catálogo que el público")
    void odontologoSinSedeLee() throws Exception {
        UsuarioPrueba odontologo = crearUsuario("ODONTOLOGO", null);
        List<String> publico = ids(leer(mockMvc.perform(get("/reservas-publicas/catalogo/servicios")).andReturn()));
        for (String sedeId : new String[]{null, SEDE_CHILCA_1}) {
            MvcResult r = listar(odontologo, sedeId);
            assertThat(r.getResponse().getStatus()).as("sedeId=" + sedeId).isEqualTo(200);
            assertThat(ids(leer(r))).containsExactlyInAnyOrderElementsOf(activosEnBd()).containsAll(publico);
        }
    }

    @Test
    @DisplayName("GET /servicios/admin: incluye inactivos, orden por orden y nombre; RECEPCION 403")
    void listarTodosAdmin() throws Exception {
        UsuarioPrueba admin = adminGeneral();
        String t = tag();
        Map<String, Object> b1 = cuerpoServicio("Zeta Orden " + t);
        b1.put("orden", 500);
        Map<String, Object> b2 = cuerpoServicio("Alfa Orden " + t);
        b2.put("orden", 500);
        String idZ = crearServicio(admin, b1).get("id").asText();
        String idA = crearServicio(admin, b2).get("id").asText();
        jdbc.update("UPDATE servicios_web SET activo = false WHERE id = ?", idZ);

        MvcResult r = mockMvc.perform(get("/servicios/admin").header("Authorization", admin.bearer())).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        JsonNode lista = leer(r);
        assertThat(ids(lista)).containsExactlyElementsOf(
                jdbc.queryForList("SELECT id FROM servicios_web WHERE unidad_de_negocio = 'VILLADENT' ORDER BY orden, nombre", String.class));
        assertThat(ids(lista)).doesNotContain("srv-1"); // los servicios genéricos de ejemplo se quitaron en V74
        assertThat(ids(lista).indexOf(idA)).isLessThan(ids(lista).indexOf(idZ));
        assertThat(buscarPorId(lista, idZ).get("activo").asBoolean()).isFalse();
        lista.forEach(n -> assertThat(nombresDeCampos(n)).containsExactlyInAnyOrderElementsOf(CAMPOS_SERVICIO));

        UsuarioPrueba recepcion = crearUsuario("RECEPCION", SEDE_CHILCA_1);
        assertThat(mockMvc.perform(get("/servicios/admin").header("Authorization", recepcion.bearer()))
                .andReturn().getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("POST /servicios: 201, id slug sin prefijo, recortes, descripción en blanco null, orden nulo 0, precio nulo null")
    void crear() throws Exception {
        String t = tag();
        Map<String, Object> cuerpo = cuerpoServicio("  Limpieza Dental Ñandú " + t + "  ");
        cuerpo.put("especialidad", "  Periodoncia  ");
        cuerpo.put("descripcion", "   ");
        JsonNode s = crearServicio(adminGeneral(), cuerpo);

        assertThat(nombresDeCampos(s)).containsExactlyInAnyOrderElementsOf(CAMPOS_SERVICIO);
        assertThat(s.get("id").asText()).isEqualTo("limpieza-dental-nandu-" + t);
        assertThat(s.get("nombre").asText()).isEqualTo("Limpieza Dental Ñandú " + t);
        assertThat(s.get("especialidad").asText()).isEqualTo("Periodoncia");
        assertThat(s.get("descripcion").isNull()).isTrue();
        assertThat(s.get("duracionMin").asInt()).isEqualTo(30);
        assertThat(s.has("precio")).isTrue();
        assertThat(s.get("precio").isNull()).isTrue();
        assertThat(s.get("orden").asInt()).isZero();
        assertThat(s.get("activo").asBoolean()).isTrue();

        Map<String, Object> fila = jdbc.queryForMap("SELECT descripcion, precio_desde, orden, activo FROM servicios_web "
                + "WHERE id = ?", "limpieza-dental-nandu-" + t);
        assertThat(fila.get("descripcion")).isNull();
        assertThat(fila.get("precio_desde")).isNull();
        assertThat(((Number) fila.get("orden")).intValue()).isZero();
        assertThat(fila.get("activo")).isEqualTo(true);
    }

    @Test
    @DisplayName("POST /servicios: sufijo -2, recorte a 40 y 'servicio' como respaldo para nombres sin letras")
    void crearIds() throws Exception {
        UsuarioPrueba admin = adminGeneral();
        String t = tag();
        assertThat(crearServicio(admin, cuerpoServicio("Corona " + t)).get("id").asText()).isEqualTo("corona-" + t);
        assertThat(crearServicio(admin, cuerpoServicio("CORONA!! " + t)).get("id").asText()).isEqualTo("corona-" + t + "-2");

        String a32 = "a".repeat(32);
        String t2 = tag();
        assertThat(crearServicio(admin, cuerpoServicio(t2 + " " + a32 + " bbbbbbbb")).get("id").asText())
                .isEqualTo(t2 + "-" + a32);

        boolean yaExistia = existeServicio("servicio");
        String id = crearServicio(admin, cuerpoServicio("### ¿?")).get("id").asText();
        if (yaExistia) {
            assertThat(id).matches("servicio-\\d+");
        } else {
            assertThat(id).isEqualTo("servicio");
        }
    }

    @Test
    @DisplayName("B-025: precio con más de 2 decimales → 400 en POST y PUT (nada se guarda ni se audita); con 2 decimales, respuesta = BD = listados")
    void precioConMasDeDosDecimales400() throws Exception {
        UsuarioPrueba admin = adminGeneral();
        String t = tag();
        String mensaje = "El precio admite como máximo 8 dígitos enteros y 2 decimales";
        Map<String, Object> raro = cuerpoServicio("Precio Raro " + t);
        raro.put("precio", new BigDecimal("99.999"));
        MvcResult r = mockMvc.perform(post("/servicios").header("Authorization", admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(json(raro))).andReturn();
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(400);
        assertThat(leer(r).get("error").asText()).isEqualTo("Validación fallida");
        assertThat(leer(r).get("detalles").get("precio").asText()).isEqualTo(mensaje);
        assertThat(existeServicio("precio-raro-" + t)).isFalse();

        Map<String, Object> cuerpo = cuerpoServicio("Precio Exacto " + t);
        cuerpo.put("precio", new BigDecimal("99.99"));
        JsonNode s = crearServicio(admin, cuerpo);
        String id = s.get("id").asText();
        assertThat(s.get("precio").decimalValue()).isEqualByComparingTo(new BigDecimal("99.99"));
        assertThat(jdbc.queryForObject("SELECT precio_desde FROM servicios_web WHERE id = ?", BigDecimal.class, id))
                .isEqualTo(new BigDecimal("99.99"));
        JsonNode admin2 = buscarPorId(leer(mockMvc.perform(get("/servicios/admin").header("Authorization", admin.bearer()))
                .andReturn()), id);
        assertThat(admin2.get("precio").decimalValue()).isEqualByComparingTo(new BigDecimal("99.99"));

        // Un precio entero se responde con la escala de la columna (2 decimales), igual que los listados
        Map<String, Object> entero = cuerpoServicio("Precio Entero " + t);
        entero.put("precio", new BigDecimal("100"));
        MvcResult rEntero = mockMvc.perform(post("/servicios").header("Authorization", admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(json(entero))).andReturn();
        assertThat(rEntero.getResponse().getStatus()).isEqualTo(201);
        assertThat(rEntero.getResponse().getContentAsString()).contains("\"precio\":100.00");

        Map<String, Object> cambio = new HashMap<>(cuerpo);
        cambio.put("precio", new BigDecimal("120.555"));
        r = mockMvc.perform(put("/servicios/" + id).header("Authorization", admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(json(cambio))).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        assertThat(leer(r).get("detalles").get("precio").asText()).isEqualTo(mensaje);
        assertThat(jdbc.queryForObject("SELECT precio_desde FROM servicios_web WHERE id = ?", BigDecimal.class, id))
                .isEqualTo(new BigDecimal("99.99"));
    }

    @Test
    @DisplayName("PUT /servicios/{id} (N-003): orden, precio y descripción omitidos o nulos conservan el valor; descripción en blanco la borra; id no cambia")
    void actualizar() throws Exception {
        UsuarioPrueba admin = adminGeneral();
        Map<String, Object> alta = cuerpoServicio("Servicio Editar " + tag());
        alta.put("orden", 950);
        alta.put("precio", new BigDecimal("80.50"));
        alta.put("descripcion", "Antes");
        String id = crearServicio(admin, alta).get("id").asText();

        Map<String, Object> cambio = cuerpoServicio("  Servicio Editado  ");
        cambio.put("duracionMin", 45);
        MvcResult r = mockMvc.perform(put("/servicios/" + id).header("Authorization", admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(json(cambio))).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        JsonNode s = leer(r);
        assertThat(nombresDeCampos(s)).containsExactlyInAnyOrderElementsOf(CAMPOS_SERVICIO);
        assertThat(s.get("id").asText()).isEqualTo(id);
        assertThat(s.get("nombre").asText()).isEqualTo("Servicio Editado");
        assertThat(s.get("duracionMin").asInt()).isEqualTo(45);
        assertThat(s.get("orden").asInt()).isEqualTo(950);
        assertThat(s.get("precio").decimalValue()).isEqualByComparingTo(new BigDecimal("80.50"));
        assertThat(s.get("descripcion").asText()).isEqualTo("Antes");
        assertThat(s.get("activo").asBoolean()).isTrue();
        Map<String, Object> fila = jdbc.queryForMap("SELECT descripcion, precio_desde FROM servicios_web WHERE id = ?", id);
        assertThat(fila.get("descripcion")).isEqualTo("Antes");
        assertThat((BigDecimal) fila.get("precio_desde")).isEqualByComparingTo(new BigDecimal("80.50"));

        // null explícito: igual que omitido (conserva)
        cambio.put("precio", null);
        cambio.put("descripcion", null);
        cambio.put("orden", 7);
        r = mockMvc.perform(put("/servicios/" + id).header("Authorization", admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(json(cambio))).andReturn();
        assertThat(leer(r).get("orden").asInt()).isEqualTo(7);
        assertThat(leer(r).get("precio").decimalValue()).isEqualByComparingTo(new BigDecimal("80.50"));
        assertThat(leer(r).get("descripcion").asText()).isEqualTo("Antes");

        // valores enviados: se aplican; descripción en blanco la borra
        cambio.put("precio", new BigDecimal("95.00"));
        cambio.put("descripcion", "   ");
        r = mockMvc.perform(put("/servicios/" + id).header("Authorization", admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(json(cambio))).andReturn();
        assertThat(leer(r).get("precio").decimalValue()).isEqualByComparingTo(new BigDecimal("95.00"));
        assertThat(leer(r).get("descripcion").isNull()).isTrue();
        assertThat(jdbc.queryForObject("SELECT descripcion FROM servicios_web WHERE id = ?", String.class, id)).isNull();
    }

    @Test
    @DisplayName("PUT y PATCH /servicios/{id} inexistente: 400 'Servicio no encontrado: x' (no 404)")
    void inexistente400() throws Exception {
        UsuarioPrueba admin = adminGeneral();
        String id = "srv-no-existe-" + tag();
        MvcResult r = mockMvc.perform(put("/servicios/" + id).header("Authorization", admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(json(cuerpoServicio("Lo que sea")))).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        JsonNode b = leer(r);
        assertThat(b.get("status").asInt()).isEqualTo(400);
        assertThat(b.get("error").asText()).isEqualTo("Bad Request");
        assertThat(b.get("message").asText()).isEqualTo("Servicio no encontrado: " + id);
        assertThat(b.get("mensaje").asText()).isEqualTo("Servicio no encontrado: " + id);
        assertThat(nombresDeCampos(b)).containsExactlyInAnyOrder("timestamp", "status", "error", "message", "mensaje");

        r = mockMvc.perform(patch("/servicios/" + id + "/estado").param("activo", "false")
                .header("Authorization", admin.bearer())).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        assertThat(leer(r).get("message").asText()).isEqualTo("Servicio no encontrado: " + id);
        assertThat(existeServicio(id)).isFalse();
    }

    @Test
    @DisplayName("PATCH /servicios/{id}/estado: desactivar lo saca de /servicios y del catálogo; sin 'activo' 400")
    void cambiarEstado() throws Exception {
        UsuarioPrueba admin = adminGeneral();
        String id = crearServicio(admin, cuerpoServicio("Servicio Estado " + tag())).get("id").asText();

        MvcResult r = mockMvc.perform(patch("/servicios/" + id + "/estado").param("activo", "false")
                .header("Authorization", admin.bearer())).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        assertThat(leer(r).get("activo").asBoolean()).isFalse();
        assertThat(nombresDeCampos(leer(r))).containsExactlyInAnyOrderElementsOf(CAMPOS_SERVICIO);
        assertThat(ids(leer(listar(admin, null)))).doesNotContain(id);
        assertThat(ids(leer(mockMvc.perform(get("/reservas-publicas/catalogo/servicios")).andReturn()))).doesNotContain(id);

        r = mockMvc.perform(patch("/servicios/" + id + "/estado").param("activo", "true")
                .header("Authorization", admin.bearer())).andReturn();
        assertThat(leer(r).get("activo").asBoolean()).isTrue();
        assertThat(ids(leer(listar(admin, null)))).contains(id);

        r = mockMvc.perform(patch("/servicios/" + id + "/estado").header("Authorization", admin.bearer())).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        assertThat(leer(r).get("message").asText()).isEqualTo("Parámetro obligatorio faltante: activo");
    }

    @Test
    @DisplayName("Validación de POST /servicios: duración 4, 481 y nula, precio negativo, nombre en blanco → 400; 5, 480 y precio 0 válidos")
    void validacion() throws Exception {
        UsuarioPrueba admin = adminGeneral();
        Object[][] casos = {
                {"duracionMin", 4, "La duración mínima es 5 minutos"},
                {"duracionMin", 481, "La duración máxima es 480 minutos"},
                {"duracionMin", null, "La duración es obligatoria"},
                {"precio", new BigDecimal("-0.01"), "El precio no puede ser negativo"},
                {"nombre", " ", "El nombre del servicio es obligatorio"},
                {"especialidad", null, "La especialidad del servicio es obligatoria"},
        };
        for (Object[] caso : casos) {
            Map<String, Object> cuerpo = cuerpoServicio("Servicio Invalido " + tag());
            cuerpo.put((String) caso[0], caso[1]);
            MvcResult r = mockMvc.perform(post("/servicios").header("Authorization", admin.bearer())
                    .contentType(MediaType.APPLICATION_JSON).content(json(cuerpo))).andReturn();
            assertThat(r.getResponse().getStatus()).as("caso %s=%s", caso[0], caso[1]).isEqualTo(400);
            JsonNode b = leer(r);
            assertThat(b.get("error").asText()).isEqualTo("Validación fallida");
            assertThat(b.get("detalles").get((String) caso[0]).asText()).isEqualTo(caso[2]);
            assertThat(b.get("message").asText()).isEqualTo(caso[2]);
        }
        Map<String, Object> cinco = cuerpoServicio("Servicio Cinco " + tag());
        cinco.put("duracionMin", 5);
        cinco.put("precio", BigDecimal.ZERO);
        JsonNode s5 = crearServicio(admin, cinco);
        assertThat(s5.get("duracionMin").asInt()).isEqualTo(5);
        assertThat(s5.get("precio").decimalValue()).isEqualByComparingTo(BigDecimal.ZERO);
        Map<String, Object> max = cuerpoServicio("Servicio Max " + tag());
        max.put("duracionMin", 480);
        assertThat(crearServicio(admin, max).get("duracionMin").asInt()).isEqualTo(480);
    }

    @Test
    @DisplayName("B-026: desbordes de columna (precio ≥ 10^8, orden fuera de smallint) → 400 de validación con mensaje claro; los límites se aceptan; nada se guarda ni se audita")
    void desbordesDeColumna() throws Exception {
        UsuarioPrueba admin = adminGeneral();
        String t = tag();
        Object[][] casos = {
                {"precio", new BigDecimal("100000000"), "El precio admite como máximo 8 dígitos enteros y 2 decimales"},
                {"orden", 40000, "El orden debe estar entre -32768 y 32767"},
                {"orden", 32768, "El orden debe estar entre -32768 y 32767"},
                {"orden", -32769, "El orden debe estar entre -32768 y 32767"},
        };
        int i = 0;
        for (Object[] caso : casos) {
            Map<String, Object> cuerpo = cuerpoServicio("Servicio Desborde " + t + " " + (i++));
            cuerpo.put((String) caso[0], caso[1]);
            MvcResult r = mockMvc.perform(post("/servicios").header("Authorization", admin.bearer())
                    .contentType(MediaType.APPLICATION_JSON).content(json(cuerpo))).andReturn();
            assertThat(r.getResponse().getStatus()).as("%s=%s: %s", caso[0], caso[1], r.getResponse().getContentAsString())
                    .isEqualTo(400);
            JsonNode b = leer(r);
            assertThat(b.get("error").asText()).isEqualTo("Validación fallida");
            assertThat(b.get("detalles").get((String) caso[0]).asText()).isEqualTo(caso[2]);
            assertThat(b.get("message").asText()).isEqualTo(caso[2]);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM servicios_web WHERE id LIKE ?", Integer.class,
                "servicio-desborde-" + t + "%")).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria_log WHERE usuario_id = ?", Integer.class, admin.id()))
                .isZero();

        // Límites de la columna: se aceptan
        Map<String, Object> maximo = cuerpoServicio("Servicio Limite " + t);
        maximo.put("precio", new BigDecimal("99999999.99"));
        maximo.put("orden", 32767);
        JsonNode s = crearServicio(admin, maximo);
        assertThat(s.get("precio").decimalValue()).isEqualByComparingTo(new BigDecimal("99999999.99"));
        assertThat(s.get("orden").asInt()).isEqualTo(32767);
        Map<String, Object> minimo = cuerpoServicio("Servicio Limite Min " + t);
        minimo.put("orden", -32768);
        assertThat(crearServicio(admin, minimo).get("orden").asInt()).isEqualTo(-32768);
    }

    @Test
    @DisplayName("GET /reservas-publicas/catalogo/servicios anónimo: 200, Cache-Control 'max-age=300, public', solo activos, 8 claves")
    void catalogoPublico() throws Exception {
        UsuarioPrueba admin = adminGeneral();
        String idInactivo = crearServicio(admin, cuerpoServicio("Catalogo Inactivo " + tag())).get("id").asText();
        jdbc.update("UPDATE servicios_web SET activo = false WHERE id = ?", idInactivo);

        MvcResult r = mockMvc.perform(get("/reservas-publicas/catalogo/servicios")).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        assertThat(r.getResponse().getHeader("Cache-Control")).isEqualTo("max-age=300, public");
        JsonNode lista = leer(r);
        assertThat(ids(lista)).containsExactlyInAnyOrderElementsOf(visiblesEnReservaEnBd()).doesNotContain(idInactivo);
        assertThat(ordenes(lista)).isSorted();
        lista.forEach(n -> assertThat(nombresDeCampos(n)).containsExactlyInAnyOrderElementsOf(CAMPOS_SERVICIO));
        assertThat(ids(leer(listar(admin, null)))).containsAll(ids(lista));
    }
}
