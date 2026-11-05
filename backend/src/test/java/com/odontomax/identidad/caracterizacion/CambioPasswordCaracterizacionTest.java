package com.odontomax.identidad.caracterizacion;

import com.odontomax.identidad.infrastructure.persistence.entity.UsuarioEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** POST /auth/cambiar-password autenticado (AuthService.cambiarPassword L365-402). */
@Testcontainers(disabledWithoutDocker = true)
class CambioPasswordCaracterizacionTest extends IdentidadPostgresTestBase {

    private MvcResult cambiar(String token, Map<String, String> body) throws Exception {
        return mockMvc.perform(post("/auth/cambiar-password").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(json(body))).andReturn();
    }

    private Integer filasHistorial(UsuarioEntity u) {
        return jdbc.queryForObject("SELECT count(*) FROM password_historial WHERE usuario_id = ?", Integer.class, u.getId());
    }

    @Test
    @DisplayName("Cambio correcto: 200 {mensaje}; guarda la anterior en el historial; la nueva entra y la vieja no")
    void cambioCorrecto() throws Exception {
        UsuarioEntity u = crearUsuario("RECEPCION", PASSWORD);
        String token = tokenDe(u, PASSWORD);
        int antes = filasHistorial(u);

        MvcResult r = cambiar(token, Map.of("password_actual", PASSWORD, "password_nuevo", "Nueva-Clave-9"));

        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        assertThat(LoginCaracterizacionTest.campos(leer(r))).containsExactly("mensaje");
        assertThat(leer(r).get("mensaje").asText()).isEqualTo("Contraseña actualizada exitosamente.");
        assertThat(filasHistorial(u)).isEqualTo(antes + 1);
        assertThat(login(u.getEmail(), "Nueva-Clave-9", ipUnica()).getResponse().getStatus()).isEqualTo(200);
        assertThat(login(u.getEmail(), PASSWORD, ipUnica()).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("Acepta también las claves camelCase passwordActual/passwordNuevo")
    void aceptaCamelCase() throws Exception {
        UsuarioEntity u = crearUsuario("ODONTOLOGO", PASSWORD);
        MvcResult r = cambiar(tokenDe(u, PASSWORD), Map.of("passwordActual", PASSWORD, "passwordNuevo", "Nueva-Clave-8"));
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Contraseña actual incorrecta → 401 (BadCredentialsException), no 400")
    void actualIncorrecta() throws Exception {
        UsuarioEntity u = crearUsuario("ADMIN_SEDE", PASSWORD);
        MvcResult r = cambiar(tokenDe(u, PASSWORD), Map.of("password_actual", "no-es", "password_nuevo", "Nueva-Clave-7"));
        assertThat(r.getResponse().getStatus()).isEqualTo(401);
        assertThat(leer(r).get("message").asText()).isEqualTo("La contraseña actual es incorrecta.");
    }

    @Test
    @DisplayName("Nueva = actual → 400; nueva = una de las 3 anteriores → 400")
    void historial() throws Exception {
        UsuarioEntity u = crearUsuario("RECEPCION", PASSWORD);
        String token = tokenDe(u, PASSWORD);

        MvcResult igual = cambiar(token, Map.of("password_actual", PASSWORD, "password_nuevo", PASSWORD));
        assertThat(igual.getResponse().getStatus()).isEqualTo(400);
        assertThat(leer(igual).get("message").asText())
                .isEqualTo("La nueva contraseña no puede coincidir con la contraseña actual.");

        assertThat(cambiar(token, Map.of("password_actual", PASSWORD, "password_nuevo", "Segunda-1"))
                .getResponse().getStatus()).isEqualTo(200);
        // B-008: el cambio invalida el token usado; se inicia sesión de nuevo con la contraseña nueva
        String tokenNuevo = tokenDe(u, "Segunda-1");
        MvcResult vuelta = cambiar(tokenNuevo, Map.of("password_actual", "Segunda-1", "password_nuevo", PASSWORD));
        assertThat(vuelta.getResponse().getStatus()).isEqualTo(400);
        assertThat(leer(vuelta).get("message").asText()).isEqualTo(
                "La nueva contraseña no puede coincidir con ninguna de las últimas 3 contraseñas utilizadas.");
    }

    @Test
    @DisplayName("Validación: contraseña nueva de menos de 4 caracteres → 400 con detalle por campo")
    void validacionLongitud() throws Exception {
        UsuarioEntity u = crearUsuario("RECEPCION", PASSWORD);
        MvcResult r = cambiar(tokenDe(u, PASSWORD), Map.of("password_actual", PASSWORD, "password_nuevo", "abc"));
        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        assertThat(leer(r).get("detalles").get("passwordNuevo").asText())
                .isEqualTo("La contraseña debe tener al menos 4 caracteres");
    }
}
