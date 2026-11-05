package com.odontomax.identidad.caracterizacion;

import com.fasterxml.jackson.databind.JsonNode;
import com.odontomax.identidad.infrastructure.persistence.entity.UsuarioEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** POST /auth/login: contrato y reglas actuales (AuthService.login L126-197). */
@Testcontainers(disabledWithoutDocker = true)
class LoginCaracterizacionTest extends IdentidadPostgresTestBase {

    static final String MSG_CREDENCIALES = "Credenciales inválidas. Correo o contraseña incorrectos.";

    @Test
    @DisplayName("Login correcto: 200 con token Bearer, expiracionMs=8h y perfil (sin datos sensibles)")
    void loginCorrecto() throws Exception {
        UsuarioEntity u = crearUsuario("RECEPCION", PASSWORD);

        MvcResult r = login(u.getEmail(), PASSWORD, ipUnica());

        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = leer(r);
        assertThat(campos(body)).containsExactly("expiracionMs", "token", "tokenTipo", "usuario");
        assertThat(body.get("tokenTipo").asText()).isEqualTo("Bearer");
        assertThat(body.get("expiracionMs").asLong()).isEqualTo(28_800_000L);
        assertThat(body.get("token").asText()).isNotBlank();
        JsonNode usuario = body.get("usuario");
        assertThat(campos(usuario)).containsExactly("email", "id", "nombre", "rol", "sedeId");
        assertThat(usuario.get("id").asText()).isEqualTo(u.getId().toString());
        assertThat(usuario.get("email").asText()).isEqualTo(u.getEmail());
        assertThat(usuario.get("rol").asText()).isEqualTo("RECEPCION");
        assertThat(usuario.get("sedeId").asText()).isEqualTo(SEDE);

        // Claims del JWT final
        JsonNode claims = claims(body.get("token").asText());
        assertThat(claims.get("sub").asText()).isEqualTo(u.getEmail());
        assertThat(claims.get("userId").asText()).isEqualTo(u.getId().toString());
        assertThat(claims.get("rol").asText()).isEqualTo("RECEPCION");
        assertThat(claims.get("sedeId").asText()).isEqualTo(SEDE);
        assertThat(claims.has("type")).isFalse();
        assertThat(claims.get("exp").asLong() - claims.get("iat").asLong()).isEqualTo(28_800L);

        // Efecto lateral: ultimo_acceso queda registrado
        assertThat(usuarioRepository.findById(u.getId()).orElseThrow().getUltimoAcceso()).isNotNull();
    }

    @Test
    @DisplayName("El email se compara sin distinguir mayúsculas")
    void loginEmailMayusculas() throws Exception {
        UsuarioEntity u = crearUsuario("ODONTOLOGO", PASSWORD);
        MvcResult r = login(u.getEmail().toUpperCase(), PASSWORD, ipUnica());
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        assertThat(leer(r).get("usuario").get("email").asText()).isEqualTo(u.getEmail());
    }

    @Test
    @DisplayName("Contraseña incorrecta y usuario inexistente: 401 con el MISMO cuerpo (GlobalExceptionHandler)")
    void credencialesInvalidasMismoMensaje() throws Exception {
        UsuarioEntity u = crearUsuario("ADMIN_SEDE", PASSWORD);

        MvcResult malaPassword = login(u.getEmail(), "otra-clave", ipUnica());
        MvcResult inexistente = login(emailUnico("nadie"), PASSWORD, ipUnica());

        for (MvcResult r : List.of(malaPassword, inexistente)) {
            assertThat(r.getResponse().getStatus()).isEqualTo(401);
            JsonNode body = leer(r);
            assertThat(campos(body)).containsExactly("error", "mensaje", "message", "status", "timestamp");
            assertThat(body.get("status").asInt()).isEqualTo(401);
            assertThat(body.get("error").asText()).isEqualTo("Unauthorized");
            assertThat(body.get("message").asText()).isEqualTo(MSG_CREDENCIALES);
            assertThat(body.get("mensaje").asText()).isEqualTo(MSG_CREDENCIALES);
        }
    }

    @Test
    @DisplayName("Usuario inactivo con contraseña correcta: 401 con el mismo mensaje genérico")
    void usuarioInactivo() throws Exception {
        UsuarioEntity u = crearUsuario(emailUnico("inactivo"), "RECEPCION", PASSWORD, false);

        mockMvc.perform(desdeIp(post("/auth/login"), ipUnica())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", u.getEmail(), "password", PASSWORD))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(MSG_CREDENCIALES));
    }

    @Test
    @DisplayName("Cuerpo inválido: 400 'Validación fallida' con detalles por campo (antes de tocar el servicio)")
    void cuerpoInvalido() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "no-es-email", "password", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Validación fallida"))
                .andExpect(jsonPath("$.detalles.email").value("Ingrese un formato de correo electrónico válido"))
                .andExpect(jsonPath("$.detalles.password").value("La contraseña es obligatoria"));
    }

    static Set<String> campos(JsonNode n) {
        Set<String> s = new TreeSet<>();
        n.fieldNames().forEachRemaining(s::add);
        return s;
    }

    JsonNode claims(String jwt) throws Exception {
        String payload = jwt.split("\\.")[1];
        return objectMapper.readTree(new String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8));
    }
}
