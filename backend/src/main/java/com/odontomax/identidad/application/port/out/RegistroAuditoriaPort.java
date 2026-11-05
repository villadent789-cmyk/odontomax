package com.odontomax.identidad.application.port.out;

import com.odontomax.identidad.api.ClinicaUserPrincipal;

/** Escritura en la bitácora de auditoría desde identidad. Los valores por defecto los aplica el adaptador. */
public interface RegistroAuditoriaPort {

    /**
     * Evento de seguridad de la propia cuenta (2FA, contraseña): módulo SEGURIDAD, tabla usuarios e ip de la
     * petición en curso. Si falla, se ignora en silencio.
     */
    void registrarEventoSeguridad(ClinicaUserPrincipal usuario, String accion, String detalle);

    /** Operación administrativa sobre cuentas de usuario. Si falla, se registra el error en el log y se continúa. */
    void registrarOperacionUsuarios(ClinicaUserPrincipal principal, String modulo, String accion, String tabla,
                                    String registroId, String detalle, String ip);
}
