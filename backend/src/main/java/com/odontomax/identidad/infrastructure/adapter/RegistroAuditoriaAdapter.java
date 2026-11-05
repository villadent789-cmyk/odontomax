package com.odontomax.identidad.infrastructure.adapter;

import com.odontomax.auditoria.api.RegistrarAuditoriaApi;
import com.odontomax.auditoria.api.RegistroAuditoria;
import com.odontomax.identidad.api.ClinicaUserPrincipal;
import com.odontomax.identidad.api.SecurityUtils;
import com.odontomax.identidad.application.port.out.RegistroAuditoriaPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Valores por defecto y gestión de errores de la auditoría de identidad. Escribe solo a través de
 * {@link RegistrarAuditoriaApi}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RegistroAuditoriaAdapter implements RegistroAuditoriaPort {

    private final RegistrarAuditoriaApi registrarAuditoriaApi;

    @Override
    public void registrarEventoSeguridad(ClinicaUserPrincipal user, String accion, String detalle) {
        try {
            registrarAuditoriaApi.registrar(new RegistroAuditoria(
                    user != null ? user.id() : null,
                    user != null ? user.nombre() : "Sistema",
                    user != null ? user.rol() : "ANONYMOUS",
                    user != null ? user.sedeId() : null,
                    "SEGURIDAD",
                    accion,
                    "usuarios",
                    null,
                    detalle,
                    SecurityUtils.getClientIp()));
        } catch (Exception ignored) {
        }
    }

    @Override
    public void registrarOperacionUsuarios(ClinicaUserPrincipal principal, String modulo, String accion, String tabla,
                                           String registroId, String detalle, String ip) {
        try {
            registrarAuditoriaApi.registrar(new RegistroAuditoria(
                    principal != null ? principal.id() : null,
                    principal != null ? principal.nombre() : "SISTEMA",
                    principal != null ? principal.rol() : "ADMIN_GENERAL",
                    principal != null ? principal.sedeId() : null,
                    modulo,
                    accion,
                    tabla,
                    registroId,
                    detalle,
                    ip != null ? ip : "127.0.0.1"));
        } catch (Exception e) {
            log.error("Error al registrar auditoría de usuarios: {}", e.getMessage());
        }
    }
}
