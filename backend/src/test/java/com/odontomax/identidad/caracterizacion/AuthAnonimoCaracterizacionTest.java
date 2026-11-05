package com.odontomax.identidad.caracterizacion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Peticiones SIN token a /auth/** protegidas con @PreAuthorize, contra el contexto completo.
 *
 * /auth/** es permitAll en el filtro: el anónimo llega al proxy de @PreAuthorize, que lanza
 * AuthorizationDeniedException dentro de MVC; GlobalExceptionHandler.handleAccessDenied la convierte
 * en 403 (no 401) y el cuerpo lleva "mensaje" (no es el JSON de JwtAuthenticationEntryPoint).
 */
@Testcontainers(disabledWithoutDocker = true)
class AuthAnonimoCaracterizacionTest extends IdentidadPostgresTestBase {

    @ParameterizedTest(name = "{0} sin token → 403 de GlobalExceptionHandler")
    @CsvSource(delimiter = '|', value = {
            "/auth/2fa/configurar         | ",
            "/auth/2fa/verificar-activar  | {\"codigo\":123456}",
            "/auth/2fa/desactivar         | {\"password_actual\":\"Actual-1\",\"codigo\":\"123456\"}",
            "/auth/cambiar-password       | {\"password_actual\":\"Actual-1\",\"password_nuevo\":\"Nueva-12\"}"
    })
    @DisplayName("Anónimo en /auth/2fa/* y /auth/cambiar-password → 403 (el usuario esperaba 401)")
    void anonimoRecibe403(String ruta, String cuerpo) throws Exception {
        var req = post(ruta);
        if (cuerpo != null) {
            req = req.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
        }
        MvcResult r = mockMvc.perform(req).andReturn();

        assertThat(r.getResponse().getStatus()).isEqualTo(403);
        var body = leer(r);
        assertThat(LoginCaracterizacionTest.campos(body)).containsExactly("error", "mensaje", "message", "status", "timestamp");
        assertThat(body.get("error").asText()).isEqualTo("Forbidden");
        assertThat(body.get("message").asText()).isEqualTo("Access Denied");
        assertThat(body.get("mensaje").asText()).isEqualTo("Access Denied");
    }

    @ParameterizedTest(name = "{0} sin token y cuerpo inválido → 400 (la validación va antes que @PreAuthorize)")
    @CsvSource(delimiter = '|', value = {
            "/auth/2fa/verificar-activar  | {}",
            "/auth/cambiar-password       | {}"
    })
    @DisplayName("Anónimo con cuerpo inválido → 400, no 403")
    void validacionAntesQueSeguridad(String ruta, String cuerpo) throws Exception {
        MvcResult r = mockMvc.perform(post(ruta).contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        assertThat(leer(r).get("error").asText()).isEqualTo("Validación fallida");
    }

    @org.junit.jupiter.api.Test
    @DisplayName("GET /auth/me sin cabecera → 401 SIN cuerpo (lo decide el controlador)")
    void meSinCabecera() throws Exception {
        MvcResult r = mockMvc.perform(get("/auth/me")).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(401);
        assertThat(r.getResponse().getContentAsString()).isEmpty();
    }
}
