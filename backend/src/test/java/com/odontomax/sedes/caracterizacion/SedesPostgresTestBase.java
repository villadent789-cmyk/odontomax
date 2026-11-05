package com.odontomax.sedes.caracterizacion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.odontomax.identidad.infrastructure.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Base de la caracterización de sedes (Fase 3, paso P0).
 *
 * Contexto completo de Spring Boot (cadena de filtros real, @PreAuthorize, servicios reales, Flyway) contra
 * PostgreSQL en Testcontainers. El contenedor es único para todas las subclases (patrón singleton) y, como
 * comparten configuración, también el contexto de Spring en caché.
 *
 * Los usuarios se insertan por SQL y los JWT se emiten con el {@link JwtTokenProvider} real: así el filtro JWT
 * los busca en la BD como en producción, y estos tests no tocan la persistencia de identidad (regla 5 de
 * ArquitecturaCapasTest). Cada test crea sus propias sedes y servicios con nombres únicos; las sedes semilla
 * no se modifican.
 *
 * Es pública porque también la usa {@code agenda.caracterizacion.SedeValidacionCaracterizacionTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class SedesPostgresTestBase {

    public static final String SEDE_CHILCA_1 = "sede-chilca-1";
    public static final String SEDE_ELTAMBO = "sede-eltambo";

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("sedes_caract")
            .withUsername("testuser")
            .withPassword("testpass");

    static {
        if (DockerClientFactory.instance().isDockerAvailable()) {
            POSTGRES.start();
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        if (POSTGRES.isRunning()) {
            registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
            registry.add("spring.datasource.username", POSTGRES::getUsername);
            registry.add("spring.datasource.password", POSTGRES::getPassword);
            registry.add("spring.flyway.enabled", () -> "true");
            registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
            registry.add("spring.flyway.user", POSTGRES::getUsername);
            registry.add("spring.flyway.password", POSTGRES::getPassword);
            registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        }
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected JwtTokenProvider jwtTokenProvider;

    private static final Random RANDOM = new Random();

    /** Usuario insertado en BD con su JWT de sesión real (token_version 0). */
    public record UsuarioPrueba(UUID id, String email, String nombre, String rol, String sedeId, String token) {
        public String bearer() {
            return "Bearer " + token;
        }
    }

    // ---------------------------------------------------------------- fixtures

    /** Sufijo aleatorio de 6 letras minúsculas: sobrevive intacto al slug de los ids. */
    protected static String tag() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            sb.append((char) ('a' + RANDOM.nextInt(26)));
        }
        return sb.toString();
    }

    protected UsuarioPrueba crearUsuario(String rol, String sedeId) {
        UUID id = UUID.randomUUID();
        String email = rol.toLowerCase().replace('_', '-') + "-" + id.toString().substring(0, 8) + "@sedes.odontomax.pe";
        String nombre = "Usuario " + rol + " " + id.toString().substring(0, 4);
        jdbc.update("INSERT INTO usuarios (id, nombre, email, rol, sede_id, activo) VALUES (?, ?, ?, ?, ?, true)",
                id, nombre, email, rol, sedeId);
        String token = jwtTokenProvider.generateToken(id, email, nombre, rol, sedeId, 0);
        return new UsuarioPrueba(id, email, nombre, rol, sedeId, token);
    }

    protected UsuarioPrueba adminGeneral() {
        return crearUsuario("ADMIN_GENERAL", null);
    }

    /** Inserta una sede por SQL (sin pasar por el servicio ni auditar desde la aplicación). */
    protected void insertarSede(String id, String nombre, boolean activa, int sillones) {
        jdbc.update("INSERT INTO sedes (id, nombre, direccion, sillones, activa) VALUES (?, ?, ?, ?, ?)",
                id, nombre, "Dirección de " + nombre, sillones, activa);
    }

    protected boolean existeSede(String id) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM sedes WHERE id = ?", Integer.class, id);
        return n != null && n > 0;
    }

    protected boolean existeServicio(String id) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM servicios_web WHERE id = ?", Integer.class, id);
        return n != null && n > 0;
    }

    // ---------------------------------------------------------------- HTTP helpers

    protected String json(Object o) throws Exception {
        return objectMapper.writeValueAsString(o);
    }

    protected JsonNode leer(MvcResult r) throws Exception {
        return objectMapper.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    protected static MockHttpServletRequestBuilder desdeIp(MockHttpServletRequestBuilder b, String ip) {
        return b.with(req -> {
            req.setRemoteAddr(ip);
            return req;
        });
    }

    protected static List<String> nombresDeCampos(JsonNode nodo) {
        List<String> campos = new ArrayList<>();
        Iterator<String> it = nodo.fieldNames();
        it.forEachRemaining(campos::add);
        return campos;
    }

    protected static List<String> ids(JsonNode arreglo) {
        List<String> ids = new ArrayList<>();
        arreglo.forEach(n -> ids.add(n.get("id").asText()));
        return ids;
    }

    protected static JsonNode buscarPorId(JsonNode arreglo, String id) {
        for (JsonNode n : arreglo) {
            if (id.equals(n.get("id").asText())) {
                return n;
            }
        }
        return null;
    }
}
