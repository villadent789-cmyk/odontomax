package com.odontomax.application.service;

import com.odontomax.pacientes.application.dto.response.PacienteExportDTO;
import com.odontomax.identidad.api.ClinicaUserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;
import com.odontomax.pacientes.application.port.in.ExportarExpedienteUseCase;

import static org.junit.jupiter.api.Assertions.*;

/** GET /pacientes/{id}/exportar contra PostgreSQL real: devuelve el expediente y deja constancia en la auditoría. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class ExportacionPacientePostgresIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        if (postgres.isRunning()) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl);
            registry.add("spring.datasource.username", postgres::getUsername);
            registry.add("spring.datasource.password", postgres::getPassword);
            registry.add("spring.flyway.enabled", () -> "true");
            registry.add("spring.flyway.url", postgres::getJdbcUrl);
            registry.add("spring.flyway.user", postgres::getUsername);
            registry.add("spring.flyway.password", postgres::getPassword);
            registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        }
    }

    @Autowired private ExportarExpedienteUseCase exportacion;
    @Autowired private JdbcTemplate jdbc;

    private UUID pacienteId;

    @BeforeEach
    void setUp() {
        ClinicaUserPrincipal admin = new ClinicaUserPrincipal(UUID.randomUUID(), "admin@odontomax.test", "Admin", "ADMIN_GENERAL", null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(admin, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN_GENERAL"))));
        pacienteId = UUID.randomUUID();
        jdbc.update("INSERT INTO pacientes (id, dni, nombres, apellidos, sede_id) VALUES (?, ?, 'Luciana', 'Meza', 'sede-chilca-1')",
                pacienteId, String.valueOf(10_000_000 + (int) (Math.random() * 89_999_999)));
        jdbc.update("INSERT INTO tratamientos (id, paciente_id, sede_id, nombre, costo_total, estado) VALUES (?, ?, 'sede-chilca-1', 'Limpieza', 80, 'EN_PROCESO')",
                UUID.randomUUID(), pacienteId);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Exporta el expediente sin error y registra EXPORTAR_ARCO en la auditoría")
    void exportaYAudita() {
        PacienteExportDTO expediente = assertDoesNotThrow(() -> exportacion.exportarExpedienteCompleto(pacienteId));

        assertEquals("Luciana", expediente.getDatosPersonales().getNombres());
        assertEquals(1, expediente.getTratamientos().size());
        Integer auditorias = jdbc.queryForObject(
                "SELECT COUNT(*) FROM auditoria_log WHERE accion = 'EXPORTAR_ARCO' AND registro_id = ?", Integer.class, pacienteId.toString());
        assertEquals(1, auditorias);
    }
}
