package com.odontomax.pacientes.api;

/** Altas de pacientes que otros módulos necesitan (reserva pública de agenda). */
public interface RegistroPacientesModuloApi {

    /**
     * Busca al paciente por DNI (recortado); si existe sin teléfono se lo completa, y si no existe lo crea
     * (no archivado, sin saldo) con los datos recortados.
     */
    PacienteResumen obtenerOCrearPorDni(String dni, String nombres, String apellidos, String telefono, String sedeId);
}
