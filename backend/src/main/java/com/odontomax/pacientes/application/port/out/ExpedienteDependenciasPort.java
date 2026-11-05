package com.odontomax.pacientes.application.port.out;

import com.odontomax.pacientes.domain.model.RegistrosProtegidos;

import java.util.List;
import java.util.UUID;

/**
 * Único punto de contacto de pacientes con las tablas de otros módulos al eliminar (clínica, finanzas, tratamientos,
 * laboratorio y agenda). TODO: se reemplaza por las APIs de esos módulos en sus fases (4 y 5).
 */
public interface ExpedienteDependenciasPort {

    /** Cuenta los registros que impiden eliminar al paciente. */
    RegistrosProtegidos contarRegistrosProtegidos(UUID pacienteId);

    /** URLs de los archivos radiográficos del paciente (omite las vacías). Un error de consulta se propaga. */
    List<String> archivosRadiograficos(UUID pacienteId);

    /** Borra radiografías, promociones, citas, odontograma y tratamientos del paciente. */
    void eliminarDependientes(UUID pacienteId);
}
