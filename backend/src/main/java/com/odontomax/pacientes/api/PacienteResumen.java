package com.odontomax.pacientes.api;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Datos mínimos de un paciente que otros módulos necesitan, sin exponer entidades ni el modelo interno.
 *
 * <p>No existe un nombre completo único: cada consumidor conserva su fórmula (agenda usa
 * {@code nombres + " " + apellidos}; reportes, clínica y laboratorio usan {@code (nombres + " " + apellidos).trim()}),
 * por eso se exponen {@code nombres} y {@code apellidos} por separado. {@code creadoEn} puede ser null cuando el
 * resumen proviene de un alta o una actualización puntual.</p>
 */
public record PacienteResumen(
        UUID id,
        String dni,
        String nombres,
        String apellidos,
        String telefono,
        String sedeId,
        LocalDate fechaNacimiento,
        String fuenteCaptacion,
        OffsetDateTime creadoEn,
        int totalInasistencias
) {
}
