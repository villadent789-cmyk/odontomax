package com.odontomax.agenda.caracterizacion;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class CitasSillonV35PostgresIntegrationTest extends AgendaPostgresFixtures {

    @Test
    void migracionV35CompletaCitasSinColisionYOmitelasIncompatibles() throws Exception {
        String sede = "sede-v35-" + tag();
        insertarSede(sede, "V35 " + sede, true, 3);
        UUID paciente = crearPaciente(sede);
        UUID doctorConSillon = crearEspecialista(null, sede);
        UUID doctorSinSillon = crearEspecialista(null, sede);
        asignarSillon(doctorConSillon, sede, 1);

        var fechaCorregible = fechaUnica();
        var fechaConColision = fechaUnica();
        UUID corregible = insertarCita(paciente, doctorConSillon, sede, null, fechaCorregible,
                LocalTime.of(9, 0), 30, "PROGRAMADA");
        UUID colision = insertarCita(paciente, doctorConSillon, sede, null, fechaConColision,
                LocalTime.of(10, 0), 30, "PROGRAMADA");
        UUID sillonOcupado = insertarCita(paciente, null, sede, 1, fechaConColision,
                LocalTime.of(10, 15), 30, "CONFIRMADA");
        UUID sinAsignacion = insertarCita(paciente, doctorSinSillon, sede, null, fechaUnica(),
                LocalTime.of(12, 0), 30, "PROGRAMADA");

        try (var connection = jdbc.getDataSource().getConnection()) {
            new ResourceDatabasePopulator(new ClassPathResource(
                    "db/migration/V35__asignar_sillones_citas_existentes.sql")).populate(connection);
        }

        assertThat(jdbc.queryForObject("SELECT sillon_numero FROM citas WHERE id = ?", Integer.class, corregible)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT sillon_numero FROM citas WHERE id = ?", Integer.class, colision)).isNull();
        assertThat(jdbc.queryForObject("SELECT sillon_numero FROM citas WHERE id = ?", Integer.class, sillonOcupado)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT sillon_numero FROM citas WHERE id = ?", Integer.class, sinAsignacion)).isNull();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM citas WHERE id IN (?, ?) AND sillon_numero IS NULL",
                Integer.class, colision, sinAsignacion)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '35' AND success",
                Integer.class)).isEqualTo(1);

        try (var connection = jdbc.getDataSource().getConnection()) {
            new ResourceDatabasePopulator(new ClassPathResource(
                    "db/migration/V35__asignar_sillones_citas_existentes.sql")).populate(connection);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM citas WHERE id IN (?, ?) AND sillon_numero IS NULL",
                Integer.class, colision, sinAsignacion)).isEqualTo(2);
    }
}
