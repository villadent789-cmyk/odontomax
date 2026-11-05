package com.odontomax.infrastructure.logging;

import com.odontomax.identidad.api.ClinicaUserPrincipal;
import com.odontomax.identidad.api.SecurityUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filtro de observabilidad para Render y entornos de producción.
 * Emite logs estructurados en formato clave=valor con métricas de tiempo de respuesta,
 * estado HTTP, IP real, usuario autenticado y sede activa.
 * 
 * Regla de seguridad 5.2: NUNCA registra contraseñas, tokens JWT, datos de tarjetas
 * ni información personal sensible (DNI, teléfonos) de pacientes.
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class StructuredRequestLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        long startTime = System.currentTimeMillis();

        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = System.currentTimeMillis() - startTime;
            String uri = request.getRequestURI();

            // Evitar saturar logs con healthchecks recurrentes de Render / Uptime monitors
            if (!uri.endsWith("/health") && !uri.endsWith("/info")) {
                String ip = SecurityUtils.getClientIp(request);
                String method = request.getMethod();
                int status = response.getStatus();

                String usuario = "ANONYMOUS";
                String sede = "NONE";

                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof ClinicaUserPrincipal principal) {
                    usuario = principal.id() != null ? principal.id().toString() : principal.rol();
                    sede = principal.sedeId() != null ? principal.sedeId() : "GLOBAL";
                }

                // Log estructurado clave=valor para CloudWatch, Render Logs o Loki
                log.info("type=HTTP_ACCESS ip={} user={} sede={} method={} uri={} status={} duration_ms={}",
                        ip, usuario, sede, method, uri, status, durationMs);
            }
        }
    }
}
