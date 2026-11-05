package com.odontomax.identidad.infrastructure.web;

import com.odontomax.application.dto.request.UsuarioCreateRequestDTO;
import com.odontomax.application.dto.request.UsuarioResetPasswordDTO;
import com.odontomax.application.dto.request.UsuarioUpdateRequestDTO;
import com.odontomax.application.dto.response.UsuarioResponseDTO;
import com.odontomax.identidad.application.port.in.ConsultarUsuarioUseCase;
import com.odontomax.identidad.application.port.in.GestionarUsuarioUseCase;
import com.odontomax.identidad.api.ClinicaUserPrincipal;
import com.odontomax.identidad.api.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/usuarios")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN_GENERAL')")
public class UsuarioController {

    private final ConsultarUsuarioUseCase consultarUsuarioUseCase;
    private final GestionarUsuarioUseCase gestionarUsuarioUseCase;

    @GetMapping
    public ResponseEntity<List<UsuarioResponseDTO>> listarUsuarios() {
        return ResponseEntity.ok(consultarUsuarioUseCase.listarTodos());
    }

    @PostMapping
    public ResponseEntity<UsuarioResponseDTO> crearUsuario(
            @Valid @RequestBody UsuarioCreateRequestDTO request,
            HttpServletRequest servletRequest
    ) {
        ClinicaUserPrincipal admin = SecurityUtils.getCurrentUser();
        String clientIp = SecurityUtils.getClientIp(servletRequest);
        UsuarioResponseDTO nuevo = gestionarUsuarioUseCase.crearUsuario(request, admin, clientIp);
        return ResponseEntity.status(HttpStatus.CREATED).body(nuevo);
    }

    @PutMapping("/{id}")
    public ResponseEntity<UsuarioResponseDTO> actualizarUsuario(
            @PathVariable UUID id,
            @Valid @RequestBody UsuarioUpdateRequestDTO request,
            HttpServletRequest servletRequest
    ) {
        ClinicaUserPrincipal admin = SecurityUtils.getCurrentUser();
        String clientIp = SecurityUtils.getClientIp(servletRequest);
        UsuarioResponseDTO actualizado = gestionarUsuarioUseCase.actualizarUsuario(id, request, admin, clientIp);
        return ResponseEntity.ok(actualizado);
    }

    @PatchMapping("/{id}/estado")
    public ResponseEntity<UsuarioResponseDTO> cambiarEstado(
            @PathVariable UUID id,
            @RequestParam("activo") boolean activo,
            HttpServletRequest servletRequest
    ) {
        ClinicaUserPrincipal admin = SecurityUtils.getCurrentUser();
        String clientIp = SecurityUtils.getClientIp(servletRequest);
        UsuarioResponseDTO actualizado = gestionarUsuarioUseCase.cambiarEstado(id, activo, admin, clientIp);
        return ResponseEntity.ok(actualizado);
    }

    @PostMapping("/{id}/reset-password")
    public ResponseEntity<Map<String, Object>> resetearPassword(
            @PathVariable UUID id,
            @Valid @RequestBody UsuarioResetPasswordDTO request,
            HttpServletRequest servletRequest
    ) {
        ClinicaUserPrincipal admin = SecurityUtils.getCurrentUser();
        String clientIp = SecurityUtils.getClientIp(servletRequest);
        Map<String, Object> response = gestionarUsuarioUseCase.resetearPassword(id, request, admin, clientIp);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/2fa/restablecer")
    public ResponseEntity<Map<String, Object>> restablecer2FA(
            @PathVariable UUID id,
            HttpServletRequest servletRequest
    ) {
        ClinicaUserPrincipal admin = SecurityUtils.getCurrentUser();
        String clientIp = SecurityUtils.getClientIp(servletRequest);
        return ResponseEntity.ok(gestionarUsuarioUseCase.restablecer2FA(id, admin, clientIp));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> eliminarUsuario(
            @PathVariable UUID id,
            HttpServletRequest servletRequest
    ) {
        ClinicaUserPrincipal admin = SecurityUtils.getCurrentUser();
        String clientIp = SecurityUtils.getClientIp(servletRequest);
        Map<String, Object> response = gestionarUsuarioUseCase.eliminarUsuario(id, admin, clientIp);
        return ResponseEntity.ok(response);
    }
}
