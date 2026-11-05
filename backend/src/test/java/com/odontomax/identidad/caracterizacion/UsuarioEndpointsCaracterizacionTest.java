package com.odontomax.identidad.caracterizacion;

import com.fasterxml.jackson.databind.JsonNode;
import com.odontomax.identidad.infrastructure.persistence.entity.UsuarioEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** Contrato de /usuarios (UsuarioController + UsuarioService) y GET /auth/me con token válido. */
@Testcontainers(disabledWithoutDocker = true)
class UsuarioEndpointsCaracterizacionTest extends IdentidadPostgresTestBase {

    private UsuarioEntity admin;
    private String token;

    @BeforeEach
    void admin() throws Exception {
        admin = crearUsuario("ADMIN_GENERAL", PASSWORD);
        token = tokenDe(admin, PASSWORD);
    }

    private MvcResult crear(Map<String, Object> body) throws Exception {
        return mockMvc.perform(post("/usuarios").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(json(body))).andReturn();
    }

    private static Map<String, Object> alta(String email, String rol, String sede) {
        Map<String, Object> m = new java.util.HashMap<>(Map.of("nombre", "Usuario Nuevo", "email", email,
                "password", "Clave-Alta-1", "rol", rol));
        if (sede != null) {
            m.put("sedeId", sede);
        }
        return m;
    }

    private String mensaje(MvcResult r) throws Exception {
        return leer(r).get("message").asText();
    }

    @Test
    @DisplayName("GET /auth/me con token válido → 200 {id, email, nombre, rol, sedeId}")
    void perfil() throws Exception {
        MvcResult r = mockMvc.perform(get("/auth/me").header("Authorization", bearer(token))).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        JsonNode b = leer(r);
        assertThat(LoginCaracterizacionTest.campos(b)).containsExactly("email", "id", "nombre", "rol", "sedeId");
        assertThat(b.get("id").asText()).isEqualTo(admin.getId().toString());
        assertThat(b.get("sedeId").isNull()).isTrue();
    }

    @Test
    @DisplayName("GET /usuarios: lista ordenada por nombre sin distinguir mayúsculas")
    void listarOrdenado() throws Exception {
        JsonNode lista = leer(mockMvc.perform(get("/usuarios").header("Authorization", bearer(token))).andReturn());
        List<String> nombres = new ArrayList<>();
        lista.forEach(n -> nombres.add(n.get("nombre").asText()));
        List<String> ordenados = new ArrayList<>(nombres);
        ordenados.sort(String.CASE_INSENSITIVE_ORDER);
        assertThat(nombres).isEqualTo(ordenados);
    }

    @Test
    @DisplayName("POST /usuarios → 201; email en minúsculas; creadoEn null en la respuesta de alta")
    void crearUsuario() throws Exception {
        String email = emailUnico("alta");
        MvcResult r = crear(alta(email.toUpperCase(), "RECEPCION", SEDE));
        assertThat(r.getResponse().getStatus()).isEqualTo(201);
        JsonNode b = leer(r);
        assertThat(b.get("email").asText()).isEqualTo(email);
        assertThat(b.get("rol").asText()).isEqualTo("RECEPCION");
        assertThat(b.get("sedeId").asText()).isEqualTo(SEDE);
        assertThat(b.get("sedeNombre").asText()).isEqualTo(SEDE);
        assertThat(b.get("activo").asBoolean()).isTrue();
        assertThat(b.get("creadoEn").isNull()).isTrue();
        assertThat(b.get("ultimoAcceso").isNull()).isTrue();
    }

    @Test
    @DisplayName("POST /usuarios: ADMIN_GENERAL ignora la sede; duplicado y rol de sede sin sede → 400")
    void crearUsuarioReglas() throws Exception {
        MvcResult ag = crear(alta(emailUnico("ag"), "ADMIN_GENERAL", SEDE));
        assertThat(ag.getResponse().getStatus()).isEqualTo(201);
        assertThat(leer(ag).get("sedeId").isNull()).isTrue();
        assertThat(leer(ag).get("sedeNombre").asText()).isEqualTo("Todas las Sedes / General");

        MvcResult duplicado = crear(alta(admin.getEmail(), "RECEPCION", SEDE));
        assertThat(duplicado.getResponse().getStatus()).isEqualTo(400);
        assertThat(mensaje(duplicado)).isEqualTo("Ya existe una cuenta registrada con el correo: " + admin.getEmail());

        MvcResult sinSede = crear(alta(emailUnico("sinsede"), "ADMIN_SEDE", null));
        assertThat(sinSede.getResponse().getStatus()).isEqualTo(400);
        assertThat(mensaje(sinSede)).isEqualTo("El rol ADMIN_SEDE requiere asignar obligatoriamente una sede de atención.");

        MvcResult rolInvalido = crear(alta(emailUnico("rol"), "SUPERUSUARIO", SEDE));
        assertThat(rolInvalido.getResponse().getStatus()).isEqualTo(400);
        assertThat(leer(rolInvalido).get("error").asText()).isEqualTo("Validación fallida");
    }

    @Test
    @DisplayName("PUT/PATCH/reset/DELETE sobre un id inexistente → 400 (IllegalArgumentException), no 404")
    void idInexistenteEs400() throws Exception {
        UUID id = UUID.randomUUID();
        String esperado = "Usuario no encontrado con ID: " + id;
        assertThat(mensaje(mockMvc.perform(put("/usuarios/" + id).header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("nombre", "Nadie Nadie", "rol", "RECEPCION", "sedeId", SEDE)))).andReturn()))
                .isEqualTo(esperado);
        MvcResult estado = mockMvc.perform(patch("/usuarios/" + id + "/estado").param("activo", "true")
                .header("Authorization", bearer(token))).andReturn();
        assertThat(estado.getResponse().getStatus()).isEqualTo(400);
        assertThat(mensaje(estado)).isEqualTo(esperado);
        MvcResult borrar = mockMvc.perform(delete("/usuarios/" + id).header("Authorization", bearer(token))).andReturn();
        assertThat(borrar.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("Autoprotección del admin en sesión: quitarse el rol, desactivarse o borrarse → 400")
    void autoproteccion() throws Exception {
        MvcResult rol = mockMvc.perform(put("/usuarios/" + admin.getId()).header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("nombre", "Admin", "rol", "RECEPCION", "sedeId", SEDE)))).andReturn();
        assertThat(rol.getResponse().getStatus()).isEqualTo(400);
        assertThat(mensaje(rol)).isEqualTo(
                "No puede quitarse el rol de Administrador General ni desactivar su propia cuenta en sesión.");

        MvcResult estado = mockMvc.perform(patch("/usuarios/" + admin.getId() + "/estado").param("activo", "false")
                .header("Authorization", bearer(token))).andReturn();
        assertThat(estado.getResponse().getStatus()).isEqualTo(400);
        assertThat(mensaje(estado)).isEqualTo("No puede desactivar su propia cuenta de administrador en sesión.");

        MvcResult borrar = mockMvc.perform(delete("/usuarios/" + admin.getId()).header("Authorization", bearer(token)))
                .andReturn();
        assertThat(borrar.getResponse().getStatus()).isEqualTo(400);
        assertThat(mensaje(borrar)).isEqualTo("No puede eliminar su propia cuenta de administrador en sesión.");
    }

    @Test
    @DisplayName("reset-password → 200 {status, mensaje}; igual a la actual → 400")
    void resetPassword() throws Exception {
        UsuarioEntity u = crearUsuario("RECEPCION", PASSWORD);
        MvcResult igual = mockMvc.perform(post("/usuarios/" + u.getId() + "/reset-password")
                .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("newPassword", PASSWORD)))).andReturn();
        assertThat(igual.getResponse().getStatus()).isEqualTo(400);
        assertThat(mensaje(igual)).isEqualTo("La nueva contraseña no puede ser idéntica a la contraseña actual.");

        MvcResult ok = mockMvc.perform(post("/usuarios/" + u.getId() + "/reset-password")
                .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("newPassword", "Reseteada-1")))).andReturn();
        assertThat(ok.getResponse().getStatus()).isEqualTo(200);
        assertThat(LoginCaracterizacionTest.campos(leer(ok))).containsExactly("mensaje", "status");
        assertThat(leer(ok).get("status").asInt()).isEqualTo(200);
        assertThat(leer(ok).get("mensaje").asText()).isEqualTo("Contraseña restablecida correctamente para " + u.getEmail());
    }

    @Test
    @DisplayName("DELETE: sin vínculos → borrado FISICO; ODONTOLOGO con especialista vinculado → baja LOGICA")
    void eliminar() throws Exception {
        UsuarioEntity sinVinculos = crearUsuario("RECEPCION", PASSWORD);
        MvcResult fisico = mockMvc.perform(delete("/usuarios/" + sinVinculos.getId())
                .header("Authorization", bearer(token))).andReturn();
        assertThat(fisico.getResponse().getStatus()).isEqualTo(200);
        assertThat(leer(fisico).get("tipo").asText()).isEqualTo("FISICO");
        assertThat(usuarioRepository.findById(sinVinculos.getId())).isEmpty();

        String id = leer(crear(alta(emailUnico("doctor"), "ODONTOLOGO", SEDE))).get("id").asText();
        MvcResult logico = mockMvc.perform(delete("/usuarios/" + id).header("Authorization", bearer(token))).andReturn();
        assertThat(logico.getResponse().getStatus()).isEqualTo(200);
        assertThat(LoginCaracterizacionTest.campos(leer(logico))).containsExactly("mensaje", "status", "tipo");
        assertThat(leer(logico).get("tipo").asText()).isEqualTo("LOGICO");
        assertThat(usuarioRepository.findById(UUID.fromString(id)).orElseThrow().isActivo()).isFalse();
    }
}
