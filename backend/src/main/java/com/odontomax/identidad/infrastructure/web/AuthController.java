package com.odontomax.identidad.infrastructure.web;

import com.odontomax.application.dto.request.CambioPasswordRequestDTO;
import com.odontomax.application.dto.request.Desactivar2faRequestDTO;
import com.odontomax.application.dto.request.LoginRequestDTO;
import com.odontomax.application.dto.request.TotpCodigoRequestDTO;
import com.odontomax.application.dto.request.TotpLoginRequestDTO;
import com.odontomax.application.dto.request.TotpVerificarRequestDTO;
import com.odontomax.application.dto.response.LoginResponseDTO;
import com.odontomax.application.dto.response.TotpConfigurarResponseDTO;
import com.odontomax.application.dto.response.UsuarioDTO;
import com.odontomax.identidad.application.port.in.CambiarPasswordUseCase;
import com.odontomax.identidad.application.port.in.ConsultarPerfilUseCase;
import com.odontomax.identidad.application.port.in.GestionarTotpUseCase;
import com.odontomax.identidad.application.port.in.LoginUseCase;
import com.odontomax.identidad.api.ClinicaUserPrincipal;
import com.odontomax.identidad.api.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final LoginUseCase loginUseCase;
    private final ConsultarPerfilUseCase consultarPerfilUseCase;
    private final CambiarPasswordUseCase cambiarPasswordUseCase;
    private final GestionarTotpUseCase gestionarTotpUseCase;

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO request, HttpServletRequest httpRequest) {
        String clientIp = SecurityUtils.getClientIp(httpRequest);
        LoginResponseDTO response = loginUseCase.login(request, clientIp);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/2fa/login")
    public ResponseEntity<LoginResponseDTO> completarLogin2FA(@Valid @RequestBody TotpLoginRequestDTO request,
                                                              HttpServletRequest httpRequest) {
        String clientIp = SecurityUtils.getClientIp(httpRequest);
        return ResponseEntity.ok(loginUseCase.completarLogin2FA(request, clientIp));
    }

    @GetMapping("/me")
    public ResponseEntity<UsuarioDTO> getAuthenticatedUser(
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(401).build();
        }
        UsuarioDTO user = consultarPerfilUseCase.getPerfilFromToken(authHeader);
        return ResponseEntity.ok(user);
    }

    @PostMapping("/cambiar-password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> cambiarPassword(
            @Valid @RequestBody CambioPasswordRequestDTO request
    ) {
        ClinicaUserPrincipal user = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(cambiarPasswordUseCase.cambiarPassword(request, user));
    }

    @PostMapping("/2fa/configurar")
    @PreAuthorize("hasRole('ADMIN_GENERAL')")
    public ResponseEntity<TotpConfigurarResponseDTO> configurar2FA(
            @RequestBody(required = false) TotpCodigoRequestDTO request
    ) {
        ClinicaUserPrincipal user = SecurityUtils.getCurrentUser();
        String codigoActual = request != null ? request.getCodigo() : null;
        return ResponseEntity.ok(gestionarTotpUseCase.configurar2FA(user, codigoActual));
    }

    @PostMapping("/2fa/verificar-activar")
    @PreAuthorize("hasRole('ADMIN_GENERAL')")
    public ResponseEntity<Map<String, Object>> verificarYActivar2FA(
            @Valid @RequestBody TotpVerificarRequestDTO request
    ) {
        ClinicaUserPrincipal user = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(gestionarTotpUseCase.verificarYActivar2FA(request, user));
    }

    @PostMapping("/2fa/desactivar")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> desactivar2FA(
            @Valid @RequestBody Desactivar2faRequestDTO request
    ) {
        ClinicaUserPrincipal user = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(gestionarTotpUseCase.desactivar2FA(request, user));
    }
}
