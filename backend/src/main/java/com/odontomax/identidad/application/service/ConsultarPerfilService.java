package com.odontomax.identidad.application.service;

import com.odontomax.application.dto.response.UsuarioDTO;
import com.odontomax.identidad.application.port.in.ConsultarPerfilUseCase;
import com.odontomax.identidad.application.port.out.TokenJwtPort;
import com.odontomax.identidad.application.port.out.UsuarioRepository;
import com.odontomax.identidad.domain.model.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ConsultarPerfilService implements ConsultarPerfilUseCase {

    private final UsuarioRepository usuarioRepository;
    private final TokenJwtPort tokenJwtPort;

    @Override
    @Transactional(readOnly = true)
    public UsuarioDTO getPerfilFromToken(String bearerToken) {
        String token = bearerToken.startsWith("Bearer ") ? bearerToken.substring(7) : bearerToken;
        if (!tokenJwtPort.validateToken(token)) {
            throw new BadCredentialsException("Token JWT inválido o expirado");
        }

        TokenJwtPort.TokenClaims claims = tokenJwtPort.leerClaims(token);
        // El token temporal de 2FA solo sirve para completar el segundo factor, nunca como sesión
        if ("2FA_PENDING".equalsIgnoreCase(claims.tipo())) {
            throw new BadCredentialsException("Debe completar la verificación en dos factores (2FA) para acceder.");
        }
        String email = claims.subject();

        Usuario user = usuarioRepository.findByEmailIgnoreCase(email)
                .filter(Usuario::isActivo)
                .orElseThrow(() -> new BadCredentialsException("Usuario no encontrado o inactivo en el sistema"));
        // Token de una versión de credenciales anterior (contraseña o 2FA cambiados después de emitirlo)
        if (!user.aceptaVersionDeToken(claims.tokenVersion())) {
            throw new BadCredentialsException("Token JWT inválido o expirado");
        }

        return IdentidadDtos.usuarioDto(user);
    }
}
