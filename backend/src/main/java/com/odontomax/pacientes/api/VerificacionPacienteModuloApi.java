package com.odontomax.pacientes.api;

/**
 * Consulta rápida de existencia de paciente por documento para otros módulos (ej. reserva pública).
 */
public interface VerificacionPacienteModuloApi {

    boolean existePorDni(String dni);
}
