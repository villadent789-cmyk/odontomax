package com.odontomax.pacientes.application.port.out;

import com.odontomax.identidad.api.ClinicaUserPrincipal;

import java.util.UUID;

/**
 * Escritura en la bitácora de auditoría de los flujos de pacientes. Cada método conserva los valores por defecto y
 * el manejo de fallos de su flujo: un fallo de auditoría nunca interrumpe la operación de negocio.
 * Archivar no audita (solo el trigger de la tabla, B-047).
 */
public interface RegistroAuditoriaPort {

    /**
     * ELIMINAR_PACIENTE (modulo PACIENTES). Sin principal usa usuario "SISTEMA" y rol "ADMIN_GENERAL"; la sede es la del
     * principal; sin ip usa "127.0.0.1". Un fallo se registra en el log (error) y se ignora.
     */
    void registrarEliminacion(ClinicaUserPrincipal principal, UUID pacienteId, String detalle, String ip);

    /**
     * Evento clínico (modulo HISTORIA_CLINICA). Sin principal usa usuario "Sistema" y rol "ANONYMOUS"; la sede es la del
     * paciente. Un fallo se ignora en silencio.
     */
    void registrarEventoClinico(ClinicaUserPrincipal principal, String sedeIdPaciente, String accion, String tabla,
                                String registroId, String detalle, String ip);

    /**
     * EXPORTAR_ARCO (modulo HISTORIA_CLINICA, tabla pacientes). Mismos valores que un evento clínico, pero un fallo se
     * registra en el log (warn) y se ignora.
     */
    void registrarExportacionArco(ClinicaUserPrincipal principal, String sedeIdPaciente, UUID pacienteId,
                                  String detalle, String ip);

    /**
     * Apertura o lectura de historia (modulo HISTORIA_CLINICA, tabla historia_clinica_accesos): los datos del usuario y la
     * sede llegan resueltos del acceso. Un fallo se ignora en silencio.
     */
    void registrarAccesoHistoria(UUID usuarioId, String usuarioNombre, String usuarioRol, String sedeId, UUID pacienteId,
                                 String accion, String detalle, String ip);
}
