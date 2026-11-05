package com.odontomax.identidad.application.port.in;

import com.odontomax.application.dto.request.UsuarioCreateRequestDTO;
import com.odontomax.application.dto.request.UsuarioResetPasswordDTO;
import com.odontomax.application.dto.request.UsuarioUpdateRequestDTO;
import com.odontomax.application.dto.response.UsuarioResponseDTO;
import com.odontomax.identidad.api.ClinicaUserPrincipal;

import java.util.Map;
import java.util.UUID;

/** Administración de cuentas de usuario (solo ADMIN_GENERAL). */
public interface GestionarUsuarioUseCase {

    UsuarioResponseDTO crearUsuario(UsuarioCreateRequestDTO request, ClinicaUserPrincipal adminPrincipal, String clientIp);

    UsuarioResponseDTO actualizarUsuario(UUID id, UsuarioUpdateRequestDTO request, ClinicaUserPrincipal adminPrincipal, String clientIp);

    UsuarioResponseDTO cambiarEstado(UUID id, boolean activo, ClinicaUserPrincipal adminPrincipal, String clientIp);

    Map<String, Object> resetearPassword(UUID id, UsuarioResetPasswordDTO request, ClinicaUserPrincipal adminPrincipal, String clientIp);

    Map<String, Object> eliminarUsuario(UUID id, ClinicaUserPrincipal adminPrincipal, String clientIp);

    /**
     * Restablece el 2FA de OTRA cuenta (nunca la propia): borra secreto y códigos de respaldo, lo desactiva e
     * invalida sus sesiones. Queda auditado.
     */
    Map<String, Object> restablecer2FA(UUID id, ClinicaUserPrincipal adminPrincipal, String clientIp);
}
