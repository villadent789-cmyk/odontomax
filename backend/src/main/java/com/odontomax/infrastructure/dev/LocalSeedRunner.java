package com.odontomax.infrastructure.dev;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * SOLO DESARROLLO LOCAL. Siembra datos de prueba idempotentes con el perfil "local":
 * un usuario por rol (contraseñas documentadas en docs/desarrollo-local.md) y los pacientes/citas
 * de db/local/seed-local.sql. Corre antes que AdminBootstrapRunner, que así ve la tabla usuarios
 * con registros y no crea nada. LocalProfileGuard garantiza que la base es localhost.
 * Se desactiva con odontomax.local.seed=false (p. ej. para mostrar el sistema vacío sin que reaparezcan los datos de prueba).
 */
@Slf4j
@Component
@Profile("local")
@ConditionalOnProperty(name = "odontomax.local.seed", havingValue = "true", matchIfMissing = true)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LocalSeedRunner implements ApplicationRunner {

    /** Especialista sembrado por V2 (Dra. Vilcapoma) que se vincula al usuario ODONTOLOGO de prueba. */
    static final UUID ESPECIALISTA_ODONTOLOGO = UUID.fromString("10000000-0000-0000-0000-000000000002");

    record UsuarioPrueba(UUID id, String nombre, String email, String password, String rol, String sedeId) {
    }

    // SOLO DESARROLLO LOCAL: contraseñas de prueba publicadas en docs/desarrollo-local.md
    static final List<UsuarioPrueba> USUARIOS = List.of(
            new UsuarioPrueba(UUID.fromString("20000000-0000-0000-0000-000000000001"), "Doctor A (Dueño / Director)",
                    "admin.general@odontomax.test", "Local-AdminGeneral-2026", "ADMIN_GENERAL", null),
            new UsuarioPrueba(UUID.fromString("20000000-0000-0000-0000-000000000002"), "Doctor B (Dueño / Sede Centro)",
                    "admin.sede@odontomax.test", "Local-AdminSede-2026", "ADMIN_GENERAL", null),
            new UsuarioPrueba(UUID.fromString("20000000-0000-0000-0000-000000000003"), "Doctor B (Odontólogo)",
                    "odontologo@odontomax.test", "Local-Odontologo-2026", "ODONTOLOGO", "sede-chilca-1"),
            new UsuarioPrueba(UUID.fromString("20000000-0000-0000-0000-000000000004"), "Recepción Chilca 1 (local)",
                    "recepcion@odontomax.test", "Local-Recepcion-2026", "RECEPCION", "sede-chilca-1"));

    private final JdbcTemplate jdbc;
    private final DataSource dataSource;
    private final PasswordEncoder passwordEncoder;

    public LocalSeedRunner(JdbcTemplate jdbc, DataSource dataSource, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.dataSource = dataSource;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.warn("[SOLO DESARROLLO LOCAL] Perfil 'local' activo: sembrando datos de prueba (idempotente).");
        int creados = 0;
        for (UsuarioPrueba u : USUARIOS) {
            creados += jdbc.update("""
                    INSERT INTO usuarios (id, nombre, email, password_hash, rol, sede_id, activo)
                    VALUES (?, ?, ?, ?, ?, ?, true)
                    ON CONFLICT (id) DO UPDATE SET
                        nombre = EXCLUDED.nombre,
                        email = EXCLUDED.email,
                        password_hash = EXCLUDED.password_hash,
                        rol = EXCLUDED.rol,
                        sede_id = EXCLUDED.sede_id,
                        activo = true
                    """, u.id(), u.nombre(), u.email(), passwordEncoder.encode(u.password()), u.rol(), u.sedeId());
        }
        jdbc.update("UPDATE especialistas SET usuario_id = ? WHERE id = ? AND usuario_id IS NULL",
                USUARIOS.get(2).id(), ESPECIALISTA_ODONTOLOGO);

        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.setSqlScriptEncoding(StandardCharsets.UTF_8.name());
        populator.addScript(new ClassPathResource("db/local/seed-local.sql"));
        populator.execute(dataSource);

        log.warn("[SOLO DESARROLLO LOCAL] Datos de prueba listos ({} usuario(s) nuevo(s); pacientes y citas de ejemplo).",
                creados);
    }
}
