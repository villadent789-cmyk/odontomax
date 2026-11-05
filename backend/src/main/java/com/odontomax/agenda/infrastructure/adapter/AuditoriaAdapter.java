package com.odontomax.agenda.infrastructure.adapter;

import com.odontomax.agenda.application.port.out.RegistroAuditoriaPort;
import com.odontomax.auditoria.api.RegistrarAuditoriaApi;
import com.odontomax.auditoria.api.RegistroAuditoria;
import com.odontomax.identidad.api.ClinicaUserPrincipal;
import com.odontomax.identidad.api.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuditoriaAdapter implements RegistroAuditoriaPort {

    private final RegistrarAuditoriaApi registrarAuditoriaApi;

    @Override
    public void registrar(String accion, String modulo, String sedeId, String detalle) {
        ClinicaUserPrincipal principal = SecurityUtils.getCurrentUser();
        registrarAuditoriaApi.registrar(new RegistroAuditoria(
                principal != null ? principal.id() : null,
                principal != null ? principal.nombre() : "SISTEMA",
                null,
                sedeId,
                modulo,
                accion,
                null,
                null,
                detalle,
                null));
    }

    @Override
    public void registrar(String modulo, String accion, String tabla, String registroId, String detalle) {
        ClinicaUserPrincipal principal = SecurityUtils.getCurrentUser();
        registrarAuditoriaApi.registrar(new RegistroAuditoria(
                principal != null ? principal.id() : null,
                principal != null ? principal.nombre() : "SISTEMA",
                null,
                null,
                modulo,
                accion,
                tabla,
                registroId,
                detalle,
                null));
    }
}
