package com.odontomax.identidad.application.service;

import com.odontomax.application.dto.request.CambioPasswordRequestDTO;
import com.odontomax.identidad.api.ClinicaUserPrincipal;
import com.odontomax.identidad.application.port.in.CambiarPasswordUseCase;
import com.odontomax.identidad.application.port.out.JwtUserCachePort;
import com.odontomax.identidad.application.port.out.PasswordHistorialRepository;
import com.odontomax.identidad.application.port.out.RegistroAuditoriaPort;
import com.odontomax.identidad.application.port.out.UsuarioRepository;
import com.odontomax.identidad.domain.model.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class CambiarPasswordService implements CambiarPasswordUseCase {

    private final UsuarioRepository usuarioRepository;
    private final PasswordHistorialRepository passwordHistorialRepository;
    private final PasswordEncoder passwordEncoder;
    private final RegistroAuditoriaPort registroAuditoria;
    private final JwtUserCachePort jwtUserCache;

    @Override
    @Transactional
    public Map<String, String> cambiarPassword(CambioPasswordRequestDTO req, ClinicaUserPrincipal user) {
        if (user == null) {
            throw new AccessDeniedException("No autenticado");
        }

        Usuario usuario = usuarioRepository.findById(user.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));

        if (!passwordEncoder.matches(req.getPasswordActual(), usuario.getPasswordHash())) {
            throw new BadCredentialsException("La contraseña actual es incorrecta.");
        }

        // Validar que la nueva contraseña no coincida con la actual ni con las últimas 3
        if (passwordEncoder.matches(req.getPasswordNuevo(), usuario.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La nueva contraseña no puede coincidir con la contraseña actual.");
        }

        if (IdentidadDtos.coincideConHistorial(passwordEncoder, req.getPasswordNuevo(),
                passwordHistorialRepository.findUltimosTresHashes(user.id()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La nueva contraseña no puede coincidir con ninguna de las últimas 3 contraseñas utilizadas.");
        }

        // Guardar contraseña actual en el historial
        passwordHistorialRepository.guardar(user.id(), usuario.getPasswordHash());

        // Actualizar contraseña: los JWT emitidos hasta ahora (incluido el de esta petición) dejan de valer
        usuario.cambiarPassword(passwordEncoder.encode(req.getPasswordNuevo()));
        usuarioRepository.save(usuario);
        IdentidadDtos.desalojarCacheJwt(jwtUserCache, usuario);

        registroAuditoria.registrarEventoSeguridad(user, "CAMBIO_PASSWORD", "Cambio de contraseña exitoso cumpliendo política de complejidad e historial");

        return Map.of("mensaje", "Contraseña actualizada exitosamente.");
    }
}
