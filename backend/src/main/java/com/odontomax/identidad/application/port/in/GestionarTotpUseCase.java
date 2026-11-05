package com.odontomax.identidad.application.port.in;

import com.odontomax.application.dto.request.Desactivar2faRequestDTO;
import com.odontomax.application.dto.request.TotpVerificarRequestDTO;
import com.odontomax.application.dto.response.TotpConfigurarResponseDTO;
import com.odontomax.identidad.api.ClinicaUserPrincipal;

import java.util.Map;

public interface GestionarTotpUseCase {

    /**
     * Genera un secreto y códigos de respaldo nuevos (el 2FA queda pendiente de verificar). Si el 2FA ya está activo
     * exige {@code codigoActual} válido (TOTP o de respaldo); sin él no cambia nada.
     */
    TotpConfigurarResponseDTO configurar2FA(ClinicaUserPrincipal user, String codigoActual);

    Map<String, Object> verificarYActivar2FA(TotpVerificarRequestDTO request, ClinicaUserPrincipal user);

    /**
     * El propio usuario desactiva su 2FA con su contraseña actual y un código válido (TOTP o de respaldo).
     * Prohibido si su rol exige 2FA (ADMIN_GENERAL).
     */
    Map<String, Object> desactivar2FA(Desactivar2faRequestDTO request, ClinicaUserPrincipal user);
}
