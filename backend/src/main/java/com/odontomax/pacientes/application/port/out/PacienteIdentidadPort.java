package com.odontomax.pacientes.application.port.out;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/** Lectura y corrección de los datos de identidad de un paciente (nombre, DNI y fecha de nacimiento). */
public interface PacienteIdentidadPort {

    record Identidad(UUID id, String sedeId, String nombres, String apellidos, String dni, LocalDate fechaNacimiento) {
    }

    Optional<Identidad> obtener(UUID pacienteId);

    void aplicar(UUID pacienteId, String nombres, String apellidos, String dni, LocalDate fechaNacimiento);
}
