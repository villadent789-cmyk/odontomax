package com.odontomax.infrastructure.logging;

import com.odontomax.identidad.api.ClinicaUserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

class StructuredRequestLoggingFilterTest {

    private StructuredRequestLoggingFilter filter;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        filter = new StructuredRequestLoggingFilter();
        filterChain = mock(FilterChain.class);
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Debe procesar y registrar logs para solicitudes anónimas sin lanzar excepción")
    void testLoggingAnonimo() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/pacientes");
        request.setRemoteAddr("192.168.1.50");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        assertDoesNotThrow(() -> filter.doFilter(request, response, filterChain));
        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    @DisplayName("Debe registrar usuario autenticado y sede activa en log estructurado")
    void testLoggingAutenticado() throws ServletException, IOException {
        ClinicaUserPrincipal principal = new ClinicaUserPrincipal(
                UUID.randomUUID(),
                "doctor@odontomax.pe",
                "Dr. Test",
                "ODONTOLOGO",
                "sede-chilca-1"
        );
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ODONTOLOGO"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/citas");
        request.setRemoteAddr("200.48.10.5");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(201);

        assertDoesNotThrow(() -> filter.doFilter(request, response, filterChain));
        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    @DisplayName("Debe omitir logs para healthchecks de Actuator")
    void testOmitirHealthCheck() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        assertDoesNotThrow(() -> filter.doFilter(request, response, filterChain));
        verify(filterChain, times(1)).doFilter(request, response);
    }
}
