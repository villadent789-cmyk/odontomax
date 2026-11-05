package com.odontomax.identidad.infrastructure.security;

import com.odontomax.identidad.api.ClinicaUserPrincipal;
import com.odontomax.identidad.application.port.out.JwtUserCachePort;
import com.odontomax.identidad.application.port.out.JwtUserCachePort.UsuarioAutenticado;
import com.odontomax.identidad.application.port.out.UsuarioRepository;
import com.odontomax.identidad.domain.model.Usuario;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UsuarioRepository usuarioRepository;
    // Caché de ~60 s de rol, sede y estado (ver JwtUserCachePort)
    private final JwtUserCachePort userCache;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        try {
            String jwt = extractJwtFromRequest(request);

            if (StringUtils.hasText(jwt) && jwtTokenProvider.validateToken(jwt)) {
                Claims claims = jwtTokenProvider.getClaimsFromToken(jwt);
                String tokenType = claims.get("type", String.class);
                if ("2FA_PENDING".equalsIgnoreCase(tokenType)) {
                    filterChain.doFilter(request, response);
                    return;
                }

                String email = claims.getSubject();

                if (StringUtils.hasText(email)) {
                    UsuarioAutenticado cachedUser = getOrFetchUser(email.trim().toLowerCase());

                    // Solo vale el token con la versión de credenciales vigente (B-008)
                    if (cachedUser != null && cachedUser.activo()
                            && cachedUser.tokenVersion() == jwtTokenProvider.getTokenVersion(claims)) {
                        // Tomar rol y sede de la BD (con caché de ~60s), NO del token
                        String rol = cachedUser.rol();
                        String sedeId = cachedUser.sedeId();
                        String nombre = cachedUser.nombre();
                        UUID userId = cachedUser.id();

                        ClinicaUserPrincipal principal = new ClinicaUserPrincipal(userId, cachedUser.email(), nombre, rol, sedeId);

                        List<GrantedAuthority> authorities = new ArrayList<>();
                        if (StringUtils.hasText(rol)) {
                            authorities.add(new SimpleGrantedAuthority("ROLE_" + rol.toUpperCase()));
                            authorities.add(new SimpleGrantedAuthority(rol.toUpperCase()));
                        }

                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(principal, null, authorities);
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    } else {
                        log.warn("Usuario autenticado inactivo, no encontrado o con un token de una versión de credenciales anterior.");
                        SecurityContextHolder.clearContext();
                    }
                }
            }
        } catch (Exception ex) {
            log.error("No se pudo establecer la autenticación del usuario en el contexto de seguridad: {}", ex.getMessage());
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    private UsuarioAutenticado getOrFetchUser(String emailLower) {
        Optional<UsuarioAutenticado> cached = userCache.get(emailLower);
        if (cached.isPresent()) {
            return cached.get();
        }

        Optional<Usuario> optUser = usuarioRepository.findByEmailIgnoreCase(emailLower);
        if (optUser.isPresent()) {
            Usuario u = optUser.get();
            UsuarioAutenticado fresh = new UsuarioAutenticado(
                    u.getId(),
                    u.getEmail(),
                    u.getNombre(),
                    u.getRol(),
                    u.getSedeId(),
                    u.isActivo(),
                    u.getTokenVersion()
            );
            userCache.put(emailLower, fresh);
            return fresh;
        } else {
            userCache.evict(emailLower);
            return null;
        }
    }

    private String extractJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        String tokenParam = request.getParameter("token");
        if (StringUtils.hasText(tokenParam)) {
            return tokenParam;
        }
        return null;
    }
}
