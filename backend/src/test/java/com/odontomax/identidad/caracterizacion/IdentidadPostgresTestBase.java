package com.odontomax.identidad.caracterizacion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.odontomax.identidad.infrastructure.persistence.entity.UsuarioEntity;
import com.odontomax.identidad.infrastructure.persistence.repository.SpringDataUsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Base de la suite de caracterización de identidad (Fase 2, paso P0).
 *
 * Contexto completo de Spring Boot (cadena de filtros, @PreAuthorize, servicios reales, Flyway) contra
 * PostgreSQL en Testcontainers. El contenedor es único para todas las subclases (patrón singleton) y,
 * como todas comparten la misma configuración, también comparten el contexto de Spring en caché.
 *
 * Configuración fija de esta suite:
 * - El estado en memoria (fuerza bruta y caché JWT) es compartido por todo el contexto: cada test usa
 *   correos e IP únicos para no interferir con los demás.
 */
@SpringBootTest
@AutoConfigureMockMvc
abstract class IdentidadPostgresTestBase {

    static final String SEDE = "sede-chilca-1";
    static final String PASSWORD = "Clave-Inicial-2026";

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("identidad_caract")
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
    protected SpringDataUsuarioRepository usuarioRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected JdbcTemplate jdbc;

    // ---------------------------------------------------------------- fixtures

    protected static String emailUnico(String prefijo) {
        return prefijo + "-" + UUID.randomUUID().toString().substring(0, 8) + "@caract.odontomax.pe";
    }

    /** IP única por test: la clave de fuerza bruta es email|ip y la IP sale de getRemoteAddr(). */
    protected static String ipUnica() {
        UUID u = UUID.randomUUID();
        return "10." + (Math.abs(u.hashCode()) % 250) + "." + (Math.abs((int) u.getMostSignificantBits()) % 250)
                + "." + (1 + Math.abs((int) u.getLeastSignificantBits()) % 250);
    }

    protected UsuarioEntity crearUsuario(String rol, String password) {
        return crearUsuario(emailUnico(rol.toLowerCase().replace('_', '-')), rol, password, true);
    }

    protected UsuarioEntity crearUsuario(String email, String rol, String password, boolean activo) {
        return usuarioRepository.save(UsuarioEntity.builder()
                .nombre("Usuario " + rol)
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .rol(rol)
                .sedeId("ADMIN_GENERAL".equals(rol) ? null : SEDE)
                .activo(activo)
                .build());
    }

    // ---------------------------------------------------------------- HTTP helpers

    protected String json(Object o) throws Exception {
        return objectMapper.writeValueAsString(o);
    }

    protected JsonNode leer(MvcResult r) throws Exception {
        return objectMapper.readTree(r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    protected static MockHttpServletRequestBuilder desdeIp(MockHttpServletRequestBuilder b, String ip) {
        return b.with(req -> {
            req.setRemoteAddr(ip);
            return req;
        });
    }

    protected MvcResult login(String email, String password, String ip) throws Exception {
        return mockMvc.perform(desdeIp(post("/auth/login"), ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", password))))
                .andReturn();
    }

    /** Login correcto (sin 2FA) y devuelve el JWT final. */
    protected String tokenDe(UsuarioEntity u, String password) throws Exception {
        MvcResult r = login(u.getEmail(), password, ipUnica());
        if (r.getResponse().getStatus() != 200) {
            throw new IllegalStateException("Login de fixture falló: " + r.getResponse().getContentAsString());
        }
        return leer(r).get("token").asText();
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }
}
