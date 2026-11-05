package com.odontomax.openapi;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.core.util.Separators;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.odontomax.identidad.infrastructure.persistence.entity.UsuarioEntity;
import com.odontomax.identidad.infrastructure.persistence.repository.SpringDataUsuarioRepository;
import com.odontomax.pacientes.infrastructure.persistence.entity.PacienteEntity;
import com.odontomax.pacientes.infrastructure.persistence.repository.SpringDataPacienteRepository;
import com.odontomax.application.port.out.CajaPeriodoPort;
import com.odontomax.application.port.out.CajaTotalesSesion;
import com.odontomax.infrastructure.persistence.repository.SpringDataPagoLaboratorioRepository;
import io.swagger.v3.oas.models.media.StringSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Exporta el contrato OpenAPI real a {@code docs/api/openapi.json} (raíz del repositorio).
 *
 * springdoc está DESACTIVADO en application.yml; solo este test lo activa con sus propiedades, de modo que en
 * ejecución no aparece ningún endpoint nuevo. Se pide {@code /v3/api-docs} con un JWT real de ADMIN_GENERAL
 * obtenido por {@code POST /auth/login} contra PostgreSQL en Testcontainers (SecurityConfig sin cambios).
 *
 * El JSON se escribe formateado y determinista (claves ordenadas, LF, servidor normalizado a {@code /api/v1}).
 * El test falla si el documento commiteado difiere del generado; para regenerarlo:
 * {@code ./mvnw -B test -Dtest=OpenApiExportTest -Dopenapi.actualizar=true}.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "springdoc.api-docs.enabled=true",
        "springdoc.swagger-ui.enabled=false",
        "springdoc.writer-with-order-by-keys=true"
})
@AutoConfigureMockMvc
class OpenApiExportTest {

    static final String CSV = "/seguridad/matriz-permisos.csv";
    static final Set<String> VERBOS = Set.of("get", "post", "put", "patch", "delete", "head", "options", "trace");
    static final String PASSWORD = "Clave-Export-OpenApi-2026";

    static {
        // Jackson (JavaTimeModule, sin timestamps) serializa LocalTime como texto ISO ("09:30" o "09:30:15"),
        // no como el objeto {hour, minute, second, nano} que springdoc infiere por reflexión.
        SpringDocUtils.getConfig().replaceWithSchema(LocalTime.class, new StringSchema()
                .pattern("^\\d{2}:\\d{2}(:\\d{2}(\\.\\d+)?)?$")
                .example("09:30")
                .description("Hora ISO-8601 local (HH:mm o HH:mm:ss)"));
    }

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("openapi_export")
            .withUsername("testuser")
            .withPassword("testpass");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private SpringDataUsuarioRepository usuarioRepository;
    @Autowired private SpringDataPacienteRepository pacienteRepository;
    @Autowired private SpringDataPagoLaboratorioRepository pagoLaboratorioRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private CajaPeriodoPort cajaPeriodoPort;

    @Test
    @Transactional
    @DisplayName("V36 aplica PLIN en los pagos y movimientos de caja de PostgreSQL")
    void v36PermitePlinEnPagosYMovimientos() {
        UsuarioEntity usuario = usuarioRepository.save(UsuarioEntity.builder()
                .nombre("Prueba V36")
                .email("v36-plin@odontomax.pe")
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .rol("ADMIN_GENERAL")
                .activo(true)
                .build());
        usuarioRepository.flush();
        PacienteEntity paciente = pacienteRepository.save(PacienteEntity.builder()
                .dni("V36-PLIN-01")
                .nombres("Paciente")
                .apellidos("V36")
                .sedeId("sede-chilca-1")
                .build());
        pacienteRepository.flush();
        UUID sesionId = jdbcTemplate.queryForObject(
                "INSERT INTO caja_sesiones (sede_id, abierta_por) VALUES (?, ?) RETURNING id",
                UUID.class, "sede-chilca-1", usuario.getId());

        jdbcTemplate.update(
                "INSERT INTO pagos (sede_id, paciente_id, monto, metodo_pago) VALUES (?, ?, ?, ?)",
                "sede-chilca-1", paciente.getId(), new java.math.BigDecimal("1.00"), "PLIN");
        jdbcTemplate.update(
                "INSERT INTO caja_movimientos (sesion_id, tipo, concepto, monto, metodo_pago, registrado_por) "
                        + "VALUES (?, 'INGRESO', 'Pago PLIN prueba', 1.00, 'PLIN', ?)",
                sesionId, usuario.getId());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM caja_sesiones WHERE id = ? AND monto_esperado_plin IS NULL AND monto_contado_plin IS NULL",
                Integer.class, sesionId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pagos WHERE paciente_id = ? AND metodo_pago = 'PLIN'",
                Integer.class, paciente.getId())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM caja_movimientos WHERE sesion_id = ? AND metodo_pago = 'PLIN'",
                Integer.class, sesionId)).isEqualTo(1);
    }

    @Test
    @Transactional
    @DisplayName("V37 registra pagos al laboratorio como egresos y suma dos órdenes en la cuenta")
    void v37RegistraPagoLaboratorioEgresoYCuentaParcial() throws Exception {
        UsuarioEntity usuario = usuarioRepository.save(UsuarioEntity.builder()
                .nombre("Prueba V37")
                .email("v37-lab-" + UUID.randomUUID() + "@odontomax.pe")
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .rol("ADMIN_GENERAL")
                .activo(true)
                .build());
        usuarioRepository.flush();

        PacienteEntity paciente = pacienteRepository.save(PacienteEntity.builder()
                .dni("V37-" + UUID.randomUUID().toString().substring(0, 8))
                .nombres("Paciente")
                .apellidos("V37")
                .sedeId("sede-chilca-1")
                .build());
        pacienteRepository.flush();

        UUID especialistaId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO especialistas (id, nombre, especialidad, activo) VALUES (?, ?, ?, true)",
                especialistaId, "Dra. Prueba V37", "Rehabilitación Oral");
        UUID laboratorioId = UUID.fromString("50000000-0000-0000-0000-000000000001");
        UUID ordenUno = UUID.randomUUID();
        UUID ordenDos = UUID.randomUUID();
        insertarOrdenLaboratorio(ordenUno, "LAB-2026-V3701", paciente.getId(), especialistaId, laboratorioId, "500.00");
        insertarOrdenLaboratorio(ordenDos, "LAB-2026-V3702", paciente.getId(), especialistaId, laboratorioId, "200.00");

        String token = tokenAdminGeneral();
        MvcResult pago = mockMvc.perform(post("/laboratorio/ordenes/" + ordenUno + "/pagos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"monto\":250.00,\"metodoPago\":\"PLIN\",\"numeroOperacion\":\"V37-PLIN-01\"}"))
                .andReturn();
        assertThat(pago.getResponse().getStatus()).isEqualTo(201);

        pagoLaboratorioRepository.flush();
        UUID sesionId = jdbcTemplate.queryForObject(
                "SELECT caja_sesion_id FROM pagos_laboratorio WHERE orden_id = ?", UUID.class, ordenUno);
        assertThat(sesionId).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pagos_laboratorio WHERE orden_id = ? AND metodo_pago = 'PLIN'",
                Integer.class, ordenUno)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM caja_movimientos WHERE referencia_tipo = 'PAGO_LABORATORIO' "
                        + "AND referencia_id = (SELECT id FROM pagos_laboratorio WHERE orden_id = ?) "
                        + "AND tipo = 'EGRESO' AND metodo_pago = 'PLIN' AND monto = 250.00",
                Integer.class, ordenUno)).isEqualTo(1);

        CajaTotalesSesion totales = cajaPeriodoPort.calcularTotales(sesionId);
        assertThat(totales.egresosPlin()).isEqualByComparingTo("250.00");
        assertThat(totales.montoEsperadoPlin()).isEqualByComparingTo("-250.00");

        MvcResult cuentas = mockMvc.perform(get("/laboratorio/cuentas?sedeId=sede-chilca-1")
                        .header("Authorization", "Bearer " + token))
                .andReturn();
        assertThat(cuentas.getResponse().getStatus()).isEqualTo(200);
        JsonNode cuenta = objectMapper.readTree(cuentas.getResponse().getContentAsString(StandardCharsets.UTF_8)).get(0);
        assertThat(cuenta.path("totalFacturado").decimalValue()).isEqualByComparingTo("700.00");
        assertThat(cuenta.path("totalPagado").decimalValue()).isEqualByComparingTo("250.00");
        assertThat(cuenta.path("saldoPorPagar").decimalValue()).isEqualByComparingTo("450.00");
        assertThat(cuenta.findValuesAsText("estadoPago")).contains("PARCIAL");
    }

    private void insertarOrdenLaboratorio(UUID id, String codigo, UUID pacienteId, UUID especialistaId,
                                          UUID laboratorioId, String costo) {
        jdbcTemplate.update("""
                INSERT INTO ordenes_laboratorio
                    (id, codigo, paciente_id, especialista_id, sede_id, laboratorio_id, tipo_trabajo,
                     costo_laboratorio, precio_paciente, estado)
                VALUES (?, ?, ?, ?, 'sede-chilca-1', ?, 'Trabajo V37', ?, 800.00, 'ENVIADO')
                """, id, codigo, pacienteId, especialistaId, laboratorioId, new java.math.BigDecimal(costo));
    }

    @Test
    @DisplayName("Exporta /v3/api-docs a docs/api/openapi.json con los 192 endpoints de la matriz y sin desincronizarse")
    void exportarContrato() throws Exception {
        String token = tokenAdminGeneral();

        MvcResult r = mockMvc.perform(get("/v3/api-docs").header("Authorization", "Bearer " + token)).andReturn();
        assertThat(r.getResponse().getStatus()).as("GET /v3/api-docs con ADMIN_GENERAL").isEqualTo(200);
        JsonNode doc = objectMapper.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8));

        // 1. Exactamente los endpoints de la matriz de permisos (verbo + ruta)
        Set<String> enDocumento = new TreeSet<>();
        doc.path("paths").fields().forEachRemaining(p -> p.getValue().fieldNames().forEachRemaining(v -> {
            if (VERBOS.contains(v)) {
                enDocumento.add(v.toUpperCase() + " " + p.getKey());
            }
        }));
        Set<String> enCsv = endpointsDelCsv();
        Set<String> faltan = new TreeSet<>(enCsv);
        faltan.removeAll(enDocumento);
        Set<String> sobran = new TreeSet<>(enDocumento);
        sobran.removeAll(enCsv);
        assertThat(faltan).as("Endpoints de la matriz ausentes en el documento OpenAPI").isEmpty();
        assertThat(sobran).as("Endpoints del documento OpenAPI que no están en la matriz").isEmpty();
        assertThat(enDocumento).hasSize(192);

        // 2. JSON determinista y comparación con el contrato publicado
        String generado = formatear(normalizar(doc));
        Path destino = raizRepositorio().resolve("docs").resolve("api").resolve("openapi.json");

        if (Boolean.getBoolean("openapi.actualizar")) {
            Files.createDirectories(destino.getParent());
            Files.writeString(destino, generado, StandardCharsets.UTF_8);
            return;
        }
        assertThat(destino).as("No existe %s: regenerar con -Dopenapi.actualizar=true", destino).exists();
        String publicado = Files.readString(destino, StandardCharsets.UTF_8).replace("\r\n", "\n");
        if (!publicado.equals(generado)) {
            Path copia = Path.of("target", "openapi-generado.json").toAbsolutePath();
            Files.createDirectories(copia.getParent());
            Files.writeString(copia, generado, StandardCharsets.UTF_8);
        }
        assertThat(publicado)
                .as("docs/api/openapi.json está desincronizado con el código (generado en target/openapi-generado.json). "
                        + "Regenerar con -Dopenapi.actualizar=true y commitear")
                .isEqualTo(generado);
    }

    private String tokenAdminGeneral() throws Exception {
        UsuarioEntity admin = usuarioRepository.save(UsuarioEntity.builder()
                .nombre("Exportador OpenAPI")
                .email("openapi-export@odontomax.pe")
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .rol("ADMIN_GENERAL")
                .sedeId(null)
                .activo(true)
                .build());
        MvcResult login = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", admin.getEmail(), "password", PASSWORD))))
                .andReturn();
        assertThat(login.getResponse().getStatus()).as("login de ADMIN_GENERAL").isEqualTo(200);
        return objectMapper.readTree(login.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("token").asText();
    }

    private static Set<String> endpointsDelCsv() throws Exception {
        Set<String> endpoints = new TreeSet<>();
        try (var in = OpenApiExportTest.class.getResourceAsStream(CSV);
             var br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String linea;
            while ((linea = br.readLine()) != null) {
                if (linea.isBlank() || linea.startsWith("#") || linea.startsWith("num;")) {
                    continue;
                }
                String[] c = linea.split(";");
                endpoints.add(c[1] + " " + c[2]);
            }
        }
        assertThat(endpoints).hasSize(192);
        return endpoints;
    }

    /** Servidor independiente del host de la prueba: las rutas del documento son relativas a /api/v1. */
    private static JsonNode normalizar(JsonNode doc) {
        ObjectNode copia = ((ObjectNode) doc).deepCopy();
        ArrayNode servers = JsonNodeFactory.instance.arrayNode();
        servers.addObject().put("url", "/api/v1").put("description", "Context path del backend OdontoMax");
        copia.set("servers", servers);
        return ordenarClaves(copia);
    }

    private static JsonNode ordenarClaves(JsonNode nodo) {
        if (nodo.isObject()) {
            ObjectNode ordenado = JsonNodeFactory.instance.objectNode();
            Set<String> claves = new TreeSet<>();
            nodo.fieldNames().forEachRemaining(claves::add);
            claves.forEach(k -> ordenado.set(k, ordenarClaves(nodo.get(k))));
            return ordenado;
        }
        if (nodo.isArray()) {
            ArrayNode arr = JsonNodeFactory.instance.arrayNode();
            for (Iterator<JsonNode> it = nodo.elements(); it.hasNext(); ) {
                arr.add(ordenarClaves(it.next()));
            }
            return arr;
        }
        return nodo;
    }

    private static String formatear(JsonNode nodo) throws Exception {
        DefaultIndenter indentador = new DefaultIndenter("  ", "\n");
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter()
                .withSeparators(Separators.createDefaultInstance()
                        .withObjectFieldValueSpacing(Separators.Spacing.AFTER));
        printer.indentObjectsWith(indentador);
        printer.indentArraysWith(indentador);
        return new ObjectMapper().writer(printer).writeValueAsString(nodo) + "\n";
    }

    /** Surefire ejecuta con user.dir = backend/; la raíz es el primer ancestro que contiene backend/pom.xml. */
    private static Path raizRepositorio() {
        Path p = Path.of("").toAbsolutePath();
        while (p != null && !Files.exists(p.resolve("backend").resolve("pom.xml"))) {
            p = p.getParent();
        }
        if (p == null) {
            throw new IllegalStateException("No se encontró la raíz del repositorio desde " + Path.of("").toAbsolutePath());
        }
        return p;
    }
}
