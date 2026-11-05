package com.odontomax.agenda.caracterizacion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** V24 contra PostgreSQL real: horario Villadent de lunes a domingo de 09:00 a 20:00. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class HorarioVilladentPostgresIntegrationTest {

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

    @Autowired private JdbcTemplate jdbc;

    @Test
    @DisplayName("Cada especialista activo en una sede Villadent activa atiende los 7 días de 09:00 a 20:00")
    void villadentLunesADomingo() {
        List<Map<String, Object>> asignaciones = jdbc.queryForList(
                "SELECT es.especialista_id, es.sede_id FROM especialista_sedes es "
                        + "JOIN especialistas e ON e.id = es.especialista_id AND e.activo "
                        + "JOIN sedes s ON s.id = es.sede_id AND s.activa AND s.unidad_de_negocio = 'VILLADENT'");
        assertFalse(asignaciones.isEmpty(), "Las semillas deberían tener doctores asignados a sedes Villadent");

        for (Map<String, Object> a : asignaciones) {
            List<Map<String, Object>> turnos = jdbc.queryForList(
                    "SELECT dia_semana, hora_inicio::text AS inicio, hora_fin::text AS fin, activo FROM especialista_horarios "
                            + "WHERE especialista_id = ? AND sede_id = ? ORDER BY dia_semana",
                    a.get("especialista_id"), a.get("sede_id"));
            assertEquals(List.of(1, 2, 3, 4, 5, 6, 7),
                    turnos.stream().map(t -> ((Number) t.get("dia_semana")).intValue()).toList(),
                    "Días de " + a);
            for (Map<String, Object> t : turnos) {
                assertEquals("09:00:00", t.get("inicio"), "Inicio de " + a);
                assertEquals("20:00:00", t.get("fin"), "Fin de " + a);
                assertEquals(Boolean.TRUE, t.get("activo"));
            }
        }
    }

    @Test
    @DisplayName("Las sedes Villamax no reciben el horario de Villadent")
    void villamaxNoSeToca() {
        Integer turnosVillamaxDe9a20 = jdbc.queryForObject(
                "SELECT COUNT(*) FROM especialista_horarios h JOIN sedes s ON s.id = h.sede_id "
                        + "WHERE s.unidad_de_negocio = 'VILLAMAX' AND h.hora_inicio = TIME '09:00' AND h.hora_fin = TIME '20:00' "
                        + "AND h.dia_semana = 7", Integer.class);
        assertEquals(0, turnosVillamaxDe9a20);
    }
}
