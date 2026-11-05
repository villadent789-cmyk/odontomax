package com.odontomax.identidad.caracterizacion;

import com.fasterxml.jackson.databind.JsonNode;
import com.odontomax.identidad.infrastructure.persistence.entity.UsuarioEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** Ninguna respuesta de auth/perfil/usuarios expone hashes, secretos TOTP, códigos de respaldo ni hashes de token. */
@Testcontainers(disabledWithoutDocker = true)
class DatosSensiblesTest extends IdentidadPostgresTestBase {

    private static final List<String> CLAVES_PROHIBIDAS = List.of(
            "passwordhash", "password_hash", "totpsecret", "totp_secret", "totpbackupcodes", "totp_backup_codes",
            "tokenhash", "token_hash", "totpactivado", "\"password\"", "$2a$", "$2b$");

    private final List<String> respuestas = new ArrayList<>();

    private MvcResult registrar(MvcResult r) throws Exception {
        respuestas.add(r.getResponse().getContentAsString(StandardCharsets.UTF_8));
        return r;
    }

    @Test
    @DisplayName("Login, /auth/me, 2FA (fuera de /configurar) y CRUD de /usuarios no exponen datos sensibles")
    void respuestasSinDatosSensibles() throws Exception {
        UsuarioEntity admin = crearUsuario("ADMIN_GENERAL", PASSWORD);
        String tokenAdmin = tokenDe(admin, PASSWORD);

        // Un usuario con 2FA activo, para que su secreto y códigos existan en BD
        UsuarioEntity conTotp = crearUsuario("ADMIN_GENERAL", PASSWORD);
        String tokenTotp = tokenDe(conTotp, PASSWORD);
        JsonNode conf = leer(mockMvc.perform(post("/auth/2fa/configurar").header("Authorization", bearer(tokenTotp)))
                .andReturn());
        String secret = conf.get("secret").asText();
        List<String> backup = new ArrayList<>();
        conf.get("backup_codes").forEach(n -> backup.add(n.asText()));
        registrar(mockMvc.perform(post("/auth/2fa/verificar-activar").header("Authorization", bearer(tokenTotp))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("codigo", TotpTestUtil.codigoActual(secret))))).andReturn());

        registrar(login(admin.getEmail(), PASSWORD, ipUnica()));
        MvcResult paso1 = registrar(login(conTotp.getEmail(), PASSWORD, ipUnica()));
        registrar(mockMvc.perform(post("/auth/2fa/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("temp_token", leer(paso1).get("token").asText(),
                        "codigo", TotpTestUtil.seisDigitos(TotpTestUtil.codigoActual(secret)))))).andReturn());
        registrar(mockMvc.perform(get("/auth/me").header("Authorization", bearer(tokenAdmin))).andReturn());
        registrar(mockMvc.perform(get("/usuarios").header("Authorization", bearer(tokenAdmin))).andReturn());

        MvcResult creado = registrar(mockMvc.perform(post("/usuarios").header("Authorization", bearer(tokenAdmin))
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("nombre", "Nuevo Usuario",
                        "email", emailUnico("nuevo"), "password", "Clave-Nueva-1", "rol", "RECEPCION", "sedeId", SEDE))))
                .andReturn());
        assertThat(creado.getResponse().getStatus()).isEqualTo(201);
        String id = leer(creado).get("id").asText();
        registrar(mockMvc.perform(put("/usuarios/" + id).header("Authorization", bearer(tokenAdmin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("nombre", "Nuevo Editado", "rol", "RECEPCION", "sedeId", SEDE)))).andReturn());
        registrar(mockMvc.perform(patch("/usuarios/" + id + "/estado").param("activo", "false")
                .header("Authorization", bearer(tokenAdmin))).andReturn());
        registrar(mockMvc.perform(post("/usuarios/" + id + "/reset-password").header("Authorization", bearer(tokenAdmin))
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("newPassword", "Otra-Clave-2")))).andReturn());
        registrar(mockMvc.perform(delete("/usuarios/" + id).header("Authorization", bearer(tokenAdmin))).andReturn());

        assertThat(respuestas).hasSize(11);
        for (String cuerpo : respuestas) {
            String minus = cuerpo.toLowerCase();
            for (String prohibida : CLAVES_PROHIBIDAS) {
                assertThat(minus).as("respuesta %s", cuerpo).doesNotContain(prohibida);
            }
            assertThat(cuerpo).doesNotContain(secret);
            backup.forEach(c -> assertThat(cuerpo).doesNotContain(c));
        }
    }

    @Test
    @DisplayName("El JSON de /usuarios tiene exactamente los campos públicos de UsuarioResponseDTO")
    void camposDeUsuarioResponse() throws Exception {
        UsuarioEntity admin = crearUsuario("ADMIN_GENERAL", PASSWORD);
        MvcResult r = mockMvc.perform(get("/usuarios").header("Authorization", bearer(tokenDe(admin, PASSWORD)))).andReturn();
        JsonNode lista = leer(r);
        assertThat(lista.isArray()).isTrue();
        JsonNode propio = null;
        for (JsonNode n : lista) {
            if (n.get("id").asText().equals(admin.getId().toString())) {
                propio = n;
            }
        }
        assertThat(propio).isNotNull();
        assertThat(LoginCaracterizacionTest.campos(propio)).containsExactly(
                "activo", "creadoEn", "email", "id", "nombre", "rol", "sedeId", "sedeNombre", "ultimoAcceso");
        assertThat(propio.get("sedeNombre").asText()).isEqualTo("Todas las Sedes / General");
    }
}
