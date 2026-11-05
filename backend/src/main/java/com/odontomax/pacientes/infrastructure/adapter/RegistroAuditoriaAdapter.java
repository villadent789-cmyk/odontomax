package com.odontomax.pacientes.infrastructure.adapter;

import com.odontomax.auditoria.api.RegistrarAuditoriaApi;
import com.odontomax.auditoria.api.RegistroAuditoria;
import com.odontomax.identidad.api.ClinicaUserPrincipal;
import com.odontomax.pacientes.application.port.out.RegistroAuditoriaPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Único punto de contacto de pacientes con la auditoría: solo usa {@link RegistrarAuditoriaApi}. */
@Slf4j
// nombre explícito: identidad ya tiene un bean registroAuditoriaAdapter
@Component("pacientesRegistroAuditoriaAdapter")
@RequiredArgsConstructor
public class RegistroAuditoriaAdapter implements RegistroAuditoriaPort {

    private final RegistrarAuditoriaApi registrarAuditoriaApi;

    @Override
    public void registrarEliminacion(ClinicaUserPrincipal principal, UUID pacienteId, String detalle, String ip) {
        try {
            registrarAuditoriaApi.registrar(new RegistroAuditoria(
                    principal != null ? principal.id() : null,
                    principal != null ? principal.nombre() : "SISTEMA",
                    principal != null ? principal.rol() : "ADMIN_GENERAL",
                    principal != null ? principal.sedeId() : null,
                    "PACIENTES",
                    "ELIMINAR_PACIENTE",
                    "pacientes",
                    pacienteId.toString(),
                    detalle,
                    ip != null ? ip : "127.0.0.1"));
        } catch (Exception e) {
            log.error("Error al registrar auditoría de pacientes: {}", e.getMessage());
        }
    }

    @Override
    public void registrarEventoClinico(ClinicaUserPrincipal principal, String sedeIdPaciente, String accion, String tabla,
                                       String registroId, String detalle, String ip) {
        try {
            registrarAuditoriaApi.registrar(eventoClinico(principal, sedeIdPaciente, accion, tabla, registroId, detalle, ip));
        } catch (Exception ignored) {
            // la auditoría nunca interrumpe la operación clínica
        }
    }

    @Override
    public void registrarExportacionArco(ClinicaUserPrincipal principal, String sedeIdPaciente, UUID pacienteId,
                                         String detalle, String ip) {
        try {
            registrarAuditoriaApi.registrar(eventoClinico(
                    principal, sedeIdPaciente, "EXPORTAR_ARCO", "pacientes", pacienteId.toString(), detalle, ip));
        } catch (Exception e) {
            log.warn("No se pudo registrar la auditoría de exportación ARCO: {}", e.getMessage());
        }
    }

    @Override
    public void registrarAccesoHistoria(UUID usuarioId, String usuarioNombre, String usuarioRol, String sedeId,
                                        UUID pacienteId, String accion, String detalle, String ip) {
        try {
            registrarAuditoriaApi.registrar(new RegistroAuditoria(
                    usuarioId, usuarioNombre, usuarioRol, sedeId,
                    "HISTORIA_CLINICA", accion, "historia_clinica_accesos", pacienteId.toString(), detalle, ip));
        } catch (Exception ignored) {
            // la auditoría nunca interrumpe la apertura o lectura del expediente
        }
    }

    private static RegistroAuditoria eventoClinico(ClinicaUserPrincipal principal, String sedeIdPaciente, String accion,
                                                   String tabla, String registroId, String detalle, String ip) {
        return new RegistroAuditoria(
                principal != null ? principal.id() : null,
                principal != null ? principal.nombre() : "Sistema",
                principal != null ? principal.rol() : "ANONYMOUS",
                sedeIdPaciente,
                "HISTORIA_CLINICA",
                accion,
                tabla,
                registroId,
                detalle,
                ip);
    }
}
