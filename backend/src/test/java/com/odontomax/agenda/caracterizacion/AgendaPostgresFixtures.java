package com.odontomax.agenda.caracterizacion;

import com.odontomax.sedes.caracterizacion.SedesPostgresTestBase;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Base de las pruebas de agenda de la Fase 3.6: contexto completo de Spring Boot contra PostgreSQL (Testcontainers,
 * el mismo contenedor que usa {@link SedesPostgresTestBase}) con datos insertados por SQL.
 *
 * Cada test usa una fecha propia (futura y única) para no cruzarse con las demás clases, que comparten la base.
 */
public abstract class AgendaPostgresFixtures extends SedesPostgresTestBase {

    private static final AtomicInteger DIA = new AtomicInteger(0);

    /** Fecha única por llamada (años 2050 en adelante) para aislar los datos de cada test. */
    protected static LocalDate fechaUnica() {
        return LocalDate.of(2050, 1, 1).plusDays(DIA.incrementAndGet() + (long) (Math.random() * 20000));
    }

    protected UUID crearPaciente(String sedeId) {
        UUID id = UUID.randomUUID();
        String dni = String.valueOf(10_000_000 + (int) (Math.random() * 89_999_999));
        jdbc.update("INSERT INTO pacientes (id, dni, nombres, apellidos, sede_id, telefono) VALUES (?, ?, 'Paciente', ?, ?, '987654321')",
                id, dni, "Prueba " + tag(), sedeId);
        return id;
    }

    /** Especialista activo atendiendo en la sede; si {@code usuarioId} no es null queda vinculado a ese usuario. */
    protected UUID crearEspecialista(UUID usuarioId, String sedeId) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO especialistas (id, usuario_id, nombre, especialidad, activo) VALUES (?, ?, ?, 'Odontología general', true)",
                id, usuarioId, "Dr. Prueba " + tag());
        jdbc.update("INSERT INTO especialista_sedes (especialista_id, sede_id) VALUES (?, ?)", id, sedeId);
        return id;
    }

    protected void asignarSillon(UUID especialistaId, String sedeId, int numero) {
        jdbc.update("UPDATE especialista_sedes SET sillon_numero = ? WHERE especialista_id = ? AND sede_id = ?",
                numero, especialistaId, sedeId);
    }

    protected UUID insertarCita(UUID pacienteId, UUID doctorId, String sedeId, Integer sillon,
                                LocalDate fecha, LocalTime hora, int duracion, String estado) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO citas (id, paciente_id, paciente_nombre, doctor_id, sede_id, sillon_numero, motivo_consulta, "
                        + "fecha, hora, duracion_minutos, estado) VALUES (?, ?, 'Paciente de prueba', ?, ?, ?, 'Control', ?, ?, ?, ?)",
                id, pacienteId, doctorId, sedeId, sillon, fecha, java.sql.Time.valueOf(hora), duracion, estado);
        return id;
    }

    protected String estadoDe(UUID citaId) {
        return jdbc.queryForObject("SELECT estado FROM citas WHERE id = ?", String.class, citaId);
    }
}
