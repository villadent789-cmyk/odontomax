package com.odontomax.identidad.application.port.in;

import com.odontomax.application.dto.request.LoginRequestDTO;
import com.odontomax.application.dto.request.TotpLoginRequestDTO;
import com.odontomax.application.dto.response.LoginResponseDTO;

public interface LoginUseCase {

    /** Autentica con correo y contraseña (protección anti fuerza bruta por correo + IP). */
    LoginResponseDTO login(LoginRequestDTO request, String clientIp);

    /**
     * Segundo factor: valida el código TOTP o de respaldo y emite el JWT de sesión. Cada código erróneo cuenta
     * para la fuerza bruta (correo + IP) y, tras 5, el token temporal queda invalidado.
     */
    LoginResponseDTO completarLogin2FA(TotpLoginRequestDTO request, String clientIp);
}
