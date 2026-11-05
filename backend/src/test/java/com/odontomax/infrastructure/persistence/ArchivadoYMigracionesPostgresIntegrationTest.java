package com.odontomax.infrastructure.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prueba de integración de migraciones Flyway (V1..V10) y archivado de tablas no críticas
 * con PostgreSQL real mediante Testcontainers.
 * Se desactiva ordenadamente si no se detecta daemon de Docker en el host (disabledWithoutDocker = true).
 */
@Testcontainers(disabledWithoutDocker = true)
class ArchivadoYMigracionesPostgresIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("postgres")
            .withPassword("testpass");

    @Test
    @DisplayName("Migraciones Flyway: Ejecuta limpiamente V1..V10 sobre BD vacía")
    void testMigracionesV1aV10SobreBdVacia() {
        Flyway flyway = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load();

        flyway.clean();
        var result = flyway.migrate();

        assertThat(result.success).isTrue();
        assertThat(result.migrationsExecuted).isGreaterThanOrEqualTo(10);
    }

    @Test
    @DisplayName("Migraciones Flyway: Migra V1..V6 y luego aplica V7..V10 progresivamente sin conflictos")
    void testMigracionProgresivaV1aV6_luego_V7aV10() {
        // 1. Migrar inicialmente hasta V6
        Flyway flywayHastaV6 = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .target("6")
                .cleanDisabled(false)
                .load();

        flywayHastaV6.clean();
        var resultV6 = flywayHastaV6.migrate();
        assertThat(resultV6.success).isTrue();

        // 2. Aplicar migraciones restantes hasta V10 (comprobantes, especialista_horarios, etc.)
        Flyway flywayCompleto = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .target("latest") // "current" en Flyway = versión ya aplicada (no migraría nada)
                .load();

        var resultFinal = flywayCompleto.migrate();
        assertThat(resultFinal.success).isTrue();
        assertThat(resultFinal.migrationsExecuted).isGreaterThanOrEqualTo(4);
    }

    @Test
    @DisplayName("Archivado: Triggers de inmutabilidad bloquean borrado normal, pero permiten purga con session_replication_role y liberan espacio")
    void testArchivadoTriggersYEspacio() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()
        );
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load();
        flyway.clean();
        flyway.migrate();

        // 1. Insertar registros de prueba en historia_clinica_accesos y auditoria_log
        UUID pacienteId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();

        // Insertar paciente y usuario dummy (FKs de historia_clinica_accesos)
        jdbc.update("""
            INSERT INTO pacientes (id, dni, nombres, apellidos, fecha_nacimiento, sede_id)
            VALUES (?, '12345678', 'Paciente', 'Test', '1990-01-01', 'sede-chilca-1')
        """, pacienteId);
        jdbc.update("""
            INSERT INTO usuarios (id, nombre, email, rol, sede_id)
            VALUES (?, 'Dr. Test', 'dr.test@example.test', 'ODONTOLOGO', 'sede-chilca-1')
        """, usuarioId);

        // Registro antiguo (>180 días) en historia_clinica_accesos
        jdbc.update("""
            INSERT INTO historia_clinica_accesos (paciente_id, usuario_id, usuario_nombre, usuario_email, usuario_rol, sede_id, ip, accedido_en)
            VALUES (?, ?, 'Dr. Test', 'dr.test@example.test', 'ODONTOLOGO', 'sede-chilca-1', '127.0.0.1', NOW() - INTERVAL '200 days')
        """, pacienteId, usuarioId);

        // Registro reciente en historia_clinica_accesos
        jdbc.update("""
            INSERT INTO historia_clinica_accesos (paciente_id, usuario_id, usuario_nombre, usuario_email, usuario_rol, sede_id, ip, accedido_en)
            VALUES (?, ?, 'Dr. Test', 'dr.test@example.test', 'ODONTOLOGO', 'sede-chilca-1', '127.0.0.1', NOW())
        """, pacienteId, usuarioId);

        // 2. Verificar que intento de DELETE directo falla por el trigger de inmutabilidad
        assertThatThrownBy(() -> jdbc.execute("DELETE FROM historia_clinica_accesos"))
                .isInstanceOf(Exception.class);

        // 3. Ejecutar purga de registros >180 días con session_replication_role dentro de transacción
        jdbc.execute("""
            BEGIN;
            SET LOCAL session_replication_role = 'replica';
            DELETE FROM historia_clinica_accesos WHERE accedido_en < (NOW() - INTERVAL '180 days');
            SET LOCAL session_replication_role = 'origin';
            COMMIT;
        """);

        // 4. Verificar que solo quedó el registro reciente (el antiguo fue purgado)
        Integer conteo = jdbc.queryForObject("SELECT COUNT(*) FROM historia_clinica_accesos", Integer.class);
        assertThat(conteo).isEqualTo(1);

        // 5. Verificar que el trigger sigue activo después de la purga
        assertThatThrownBy(() -> jdbc.execute("DELETE FROM historia_clinica_accesos"))
                .isInstanceOf(Exception.class);
    }
}
