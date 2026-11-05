package com.odontomax.identidad.caracterizacion;

import com.odontomax.identidad.infrastructure.persistence.entity.UsuarioEntity;
import com.odontomax.identidad.infrastructure.adapter.InMemoryJwtUserCacheAdapter;
import com.odontomax.identidad.infrastructure.security.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * JwtAuthenticationFilter: validación del token y caché de usuarios de 60 s (L38-53, L109-133).
 *
 * La caché guarda rol, sede y "activo" durante 60 s y no se invalida al desactivar ni al cambiar el rol
 * (B-007). Cambiar o restablecer la contraseña sí invalida los tokens al instante (B-008, versión de token). La espera de 60 s se simula envejeciendo la entrada
 * ({@link EstadoEnMemoriaTestSupport#envejecerCacheJwt}), sin sleeps.
 */
@Testcontainers(disabledWithoutDocker = true)
class JwtSecurityCaracterizacionTest extends IdentidadPostgresTestBase {

    static final String MSG_ENTRY_POINT =
            "Acceso denegado. Se requiere un token JWT Bearer válido para acceder a este recurso clínico.";

    @Autowired
    private InMemoryJwtUserCacheAdapter jwtFilter; // caché del filtro JWT (nombre conservado: aserciones intactas)

    @Autowired
    private Environment environment;

    private ResultActions listarUsuarios(String authorization) throws Exception {
        return authorization == null
                ? mockMvc.perform(get("/usuarios"))
                : mockMvc.perform(get("/usuarios").header("Authorization", authorization));
    }

    private void expect401EntryPoint(ResultActions r) throws Exception {
        r.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value(MSG_ENTRY_POINT))
                .andExpect(jsonPath("$.path").value("/usuarios"))
                .andExpect(jsonPath("$.mensaje").doesNotExist());
    }

    private JwtTokenProvider proveedor(String secret, long expiracionMs) {
        JwtTokenProvider p = new JwtTokenProvider();
        ReflectionTestUtils.setField(p, "jwtSecret", secret);
        ReflectionTestUtils.setField(p, "jwtExpirationMs", expiracionMs);
        p.init();
        return p;
    }

    private void envejecerCache(UsuarioEntity u, long ms) {
        assertThat(EstadoEnMemoriaTestSupport.envejecerCacheJwt(jwtFilter, u.getEmail().toLowerCase(), ms)).isTrue();
    }

    @Test
    @DisplayName("Token válido → 200; sin cabecera, 'Basic', expirado, manipulado o de otra clave → 401 del entry point")
    void validacionDelToken() throws Exception {
        UsuarioEntity ag = crearUsuario("ADMIN_GENERAL", PASSWORD);
        String token = tokenDe(ag, PASSWORD);
        listarUsuarios(bearer(token)).andExpect(status().isOk());

        expect401EntryPoint(listarUsuarios(null));
        expect401EntryPoint(listarUsuarios("Basic " + token));

        String secret = environment.getProperty("security.jwt.secret");
        String expirado = proveedor(secret, -60_000L).generateToken(ag.getId(), ag.getEmail(), ag.getNombre(), "ADMIN_GENERAL", null, 0);
        expect401EntryPoint(listarUsuarios(bearer(expirado)));

        String[] partes = token.split("\\.");
        String firmaAlterada = partes[2].charAt(0) == 'A' ? "B" + partes[2].substring(1) : "A" + partes[2].substring(1);
        expect401EntryPoint(listarUsuarios(bearer(partes[0] + "." + partes[1] + "." + firmaAlterada)));

        String otraClave = proveedor("otra-clave-secreta-de-al-menos-32-caracteres!!", 60_000L)
                .generateToken(ag.getId(), ag.getEmail(), ag.getNombre(), "ADMIN_GENERAL", null, 0);
        expect401EntryPoint(listarUsuarios(bearer(otraClave)));
    }

    @Test
    @DisplayName("El rol se toma de la BD, no del token: un token que dice ADMIN_GENERAL de un RECEPCION → 403")
    void rolDesdeBdNoDesdeToken() throws Exception {
        UsuarioEntity re = crearUsuario("RECEPCION", PASSWORD);
        String secret = environment.getProperty("security.jwt.secret");
        String forjado = proveedor(secret, 60_000L).generateToken(re.getId(), re.getEmail(), re.getNombre(), "ADMIN_GENERAL", null, 0);
        listarUsuarios(bearer(forjado)).andExpect(status().isForbidden()).andExpect(jsonPath("$.mensaje").value("Access Denied"));
    }

    @Test
    @DisplayName("Usuario inactivo (sin entrada en caché) o inexistente con token firmado válido → 401")
    void inactivoOInexistente() throws Exception {
        UsuarioEntity inactivo = crearUsuario(emailUnico("inactivo"), "ADMIN_GENERAL", PASSWORD, false);
        String secret = environment.getProperty("security.jwt.secret");
        JwtTokenProvider p = proveedor(secret, 60_000L);
        expect401EntryPoint(listarUsuarios(bearer(
                p.generateToken(inactivo.getId(), inactivo.getEmail(), "x", "ADMIN_GENERAL", null, 0))));
        expect401EntryPoint(listarUsuarios(bearer(
                p.generateToken(java.util.UUID.randomUUID(), emailUnico("borrado"), "x", "ADMIN_GENERAL", null, 0))));
    }

    @Test
    @DisplayName("Desactivar con token vigente: sigue entrando hasta 60 s (caché); /auth/me corta al instante")
    void desactivacionTardaHasta60s() throws Exception {
        UsuarioEntity admin = crearUsuario("ADMIN_GENERAL", PASSWORD);
        UsuarioEntity victima = crearUsuario("ADMIN_GENERAL", PASSWORD);
        String tokenAdmin = tokenDe(admin, PASSWORD);
        String tokenVictima = tokenDe(victima, PASSWORD);
        listarUsuarios(bearer(tokenVictima)).andExpect(status().isOk()); // calienta la caché

        mockMvc.perform(patch("/usuarios/" + victima.getId() + "/estado").param("activo", "false")
                .header("Authorization", bearer(tokenAdmin))).andExpect(status().isOk());

        listarUsuarios(bearer(tokenVictima)).andExpect(status().isOk());
        mockMvc.perform(get("/auth/me").header("Authorization", bearer(tokenVictima)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Usuario no encontrado o inactivo en el sistema"));

        envejecerCache(victima, 59_000L);
        listarUsuarios(bearer(tokenVictima)).andExpect(status().isOk());
        envejecerCache(victima, 2_000L);
        expect401EntryPoint(listarUsuarios(bearer(tokenVictima)));
    }

    @Test
    @DisplayName("Reactivar: el estado inactivo también queda en caché y el usuario sigue fuera hasta 60 s")
    void reactivacionTambienTarda() throws Exception {
        UsuarioEntity admin = crearUsuario("ADMIN_GENERAL", PASSWORD);
        UsuarioEntity u = crearUsuario("ADMIN_GENERAL", PASSWORD);
        String tokenAdmin = tokenDe(admin, PASSWORD);
        String tokenU = tokenDe(u, PASSWORD);
        mockMvc.perform(patch("/usuarios/" + u.getId() + "/estado").param("activo", "false")
                .header("Authorization", bearer(tokenAdmin))).andExpect(status().isOk());
        expect401EntryPoint(listarUsuarios(bearer(tokenU))); // primera petición: lee la BD y cachea activo=false
        assertThat(EstadoEnMemoriaTestSupport.hayEntradaEnCacheJwt(jwtFilter, u.getEmail())).isTrue();

        mockMvc.perform(patch("/usuarios/" + u.getId() + "/estado").param("activo", "true")
                .header("Authorization", bearer(tokenAdmin))).andExpect(status().isOk());
        expect401EntryPoint(listarUsuarios(bearer(tokenU)));
        envejecerCache(u, 61_000L);
        listarUsuarios(bearer(tokenU)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Cambio de rol: el rol anterior sigue aplicándose hasta 60 s; después rige el nuevo con el MISMO token")
    void cambioDeRolTardaHasta60s() throws Exception {
        UsuarioEntity admin = crearUsuario("ADMIN_GENERAL", PASSWORD);
        UsuarioEntity u = crearUsuario("ADMIN_GENERAL", PASSWORD);
        String tokenAdmin = tokenDe(admin, PASSWORD);
        String tokenU = tokenDe(u, PASSWORD);
        listarUsuarios(bearer(tokenU)).andExpect(status().isOk());

        mockMvc.perform(put("/usuarios/" + u.getId()).header("Authorization", bearer(tokenAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nombre", "Degradado", "rol", "RECEPCION", "sedeId", SEDE))))
                .andExpect(status().isOk());

        listarUsuarios(bearer(tokenU)).andExpect(status().isOk());
        mockMvc.perform(get("/auth/me").header("Authorization", bearer(tokenU)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rol").value("RECEPCION"));

        envejecerCache(u, 61_000L);
        listarUsuarios(bearer(tokenU)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("B-008: cambiar, resetear (admin) o restablecer la contraseña invalida al instante los JWT anteriores")
    void cambioDePasswordInvalidaTokens() throws Exception {
        UsuarioEntity admin = crearUsuario("ADMIN_GENERAL", PASSWORD);
        UsuarioEntity u = crearUsuario("ADMIN_GENERAL", PASSWORD);
        String tokenAdmin = tokenDe(admin, PASSWORD);
        String tokenViejo = tokenDe(u, PASSWORD);
        listarUsuarios(bearer(tokenViejo)).andExpect(status().isOk()); // calienta la caché

        // 1) Cambio de contraseña propio: el token usado deja de valer sin esperar a la caché
        mockMvc.perform(post("/auth/cambiar-password").header("Authorization", bearer(tokenViejo))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("password_actual", PASSWORD, "password_nuevo", "Cambiada-1"))))
                .andExpect(status().isOk());
        expect401EntryPoint(listarUsuarios(bearer(tokenViejo)));
        mockMvc.perform(get("/auth/me").header("Authorization", bearer(tokenViejo))).andExpect(status().isUnauthorized());

        String tokenSegundo = tokenDe(u, "Cambiada-1");
        listarUsuarios(bearer(tokenSegundo)).andExpect(status().isOk());

        // 2) Reseteo por un administrador
        mockMvc.perform(post("/usuarios/" + u.getId() + "/reset-password").header("Authorization", bearer(tokenAdmin))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("newPassword", "Reseteada-2"))))
                .andExpect(status().isOk());
        expect401EntryPoint(listarUsuarios(bearer(tokenSegundo)));
        mockMvc.perform(get("/auth/me").header("Authorization", bearer(tokenSegundo))).andExpect(status().isUnauthorized());

        String tokenTercero = tokenDe(u, "Reseteada-2");
        listarUsuarios(bearer(tokenTercero)).andExpect(status().isOk());

        // El restablecimiento solo lo hace un administrador (ya no hay recuperación por correo): dos cambios, versión 2.
        assertThat(jdbc.queryForObject("SELECT token_version FROM usuarios WHERE id = ?", Integer.class, u.getId()))
                .isEqualTo(2);

        // Tokens emitidos antes de B-008 (sin claim de versión) cuentan como versión 0
        String secret = environment.getProperty("security.jwt.secret");
        listarUsuarios(bearer(tokenSinVersion(secret, admin))).andExpect(status().isOk());
        expect401EntryPoint(listarUsuarios(bearer(tokenSinVersion(secret, u))));
    }

    /**
     * JWT de sesión con la forma de los emitidos antes de V14 (sin claim tokenVersion), firmado con la misma clave
     * que JwtTokenProvider (SHA-256 del secreto). La producción ya no puede emitirlos.
     */
    private static String tokenSinVersion(String secret, UsuarioEntity u) throws Exception {
        javax.crypto.SecretKey clave = io.jsonwebtoken.security.Keys.hmacShaKeyFor(java.security.MessageDigest
                .getInstance("SHA-256").digest(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        java.util.Date ahora = new java.util.Date();
        return io.jsonwebtoken.Jwts.builder()
                .subject(u.getEmail())
                .claim("userId", u.getId().toString())
                .claim("email", u.getEmail())
                .claim("nombre", u.getNombre())
                .claim("rol", u.getRol())
                .claim("sedeId", u.getSedeId())
                .issuedAt(ahora)
                .expiration(new java.util.Date(ahora.getTime() + 60_000L))
                .signWith(clave)
                .compact();
    }
}
