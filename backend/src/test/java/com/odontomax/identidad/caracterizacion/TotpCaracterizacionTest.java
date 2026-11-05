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
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 2FA/TOTP: configurar, activar, login en 2 pasos, códigos de respaldo y desactivar (AuthService L200-362). */
@Testcontainers(disabledWithoutDocker = true)
class TotpCaracterizacionTest extends IdentidadPostgresTestBase {

    static final String MSG_CODIGO_INVALIDO = "Código de autenticación 2FA o código de respaldo inválido.";
    static final String MSG_DESACTIVAR_INVALIDO = "Contraseña actual o código de autenticación 2FA incorrectos.";

    /** AG con 2FA ya activado. */
    record Enrolado(UsuarioEntity usuario, String secret, List<String> backupCodes) {}

    private MvcResult configurar(String token) throws Exception {
        return mockMvc.perform(post("/auth/2fa/configurar").header("Authorization", bearer(token))).andReturn();
    }

    private MvcResult activar(String token, int codigo) throws Exception {
        return mockMvc.perform(post("/auth/2fa/verificar-activar").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("codigo", codigo)))).andReturn();
    }

    private MvcResult login2fa(String tempToken, String codigo) throws Exception {
        return mockMvc.perform(post("/auth/2fa/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("temp_token", tempToken, "codigo", codigo)))).andReturn();
    }

    private Enrolado enrolarAdmin() throws Exception {
        UsuarioEntity ag = crearUsuario("ADMIN_GENERAL", PASSWORD);
        String token = tokenDe(ag, PASSWORD);
        JsonNode conf = leer(configurar(token));
        String secret = conf.get("secret").asText();
        List<String> codes = new ArrayList<>();
        conf.get("backup_codes").forEach(n -> codes.add(n.asText()));
        assertThat(activar(token, TotpTestUtil.codigoActual(secret)).getResponse().getStatus()).isEqualTo(200);
        return new Enrolado(ag, secret, codes);
    }

    private String tempToken(UsuarioEntity u) throws Exception {
        MvcResult r = login(u.getEmail(), PASSWORD, ipUnica());
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        return leer(r).get("token").asText();
    }

    @Test
    @DisplayName("configurar: 200 con secret, URL otpauth, QR y 5 códigos de respaldo (snake y camel); 2FA aún inactivo")
    void configurar2fa() throws Exception {
        UsuarioEntity ag = crearUsuario("ADMIN_GENERAL", PASSWORD);
        MvcResult r = configurar(tokenDe(ag, PASSWORD));

        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        JsonNode b = leer(r);
        assertThat(LoginCaracterizacionTest.campos(b)).containsExactly(
                "backupCodes", "backup_codes", "otpauthUrl", "otpauth_url", "qrCode", "qr_code", "secret");
        String secret = b.get("secret").asText();
        assertThat(secret).matches("[A-Z2-7]{32}");
        assertThat(b.get("otpauth_url").asText())
                .isEqualTo("otpauth://totp/OdontoMax:" + ag.getEmail() + "?secret=" + secret + "&issuer=OdontoMax");
        assertThat(b.get("qr_code").asText()).startsWith("data:image/png;base64,");
        assertThat(b.get("backup_codes")).hasSize(5);
        b.get("backup_codes").forEach(c -> assertThat(c.asText()).matches("[A-Z2-7]{8}"));
        assertThat(b.get("backupCodes")).isEqualTo(b.get("backup_codes"));

        UsuarioEntity enBd = usuarioRepository.findById(ag.getId()).orElseThrow();
        assertThat(enBd.isTotpActivado()).isFalse();
        assertThat(enBd.getTotpSecret()).isEqualTo(secret);
        String columna = jdbc.queryForObject("SELECT totp_secret FROM usuarios WHERE id = ?", String.class, ag.getId());
        assertThat(columna).isNotEqualTo(secret); // cifrado en reposo (AES-GCM)
    }

    @Test
    @DisplayName("verificar-activar: sin configurar → 400; código incorrecto → 400; correcto → 200 {mensaje, activo} sin códigos")
    void verificarActivar() throws Exception {
        UsuarioEntity ag = crearUsuario("ADMIN_GENERAL", PASSWORD);
        String token = tokenDe(ag, PASSWORD);

        mockMvc.perform(post("/auth/2fa/verificar-activar").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"codigo\":123456}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Debe iniciar primero la configuración de 2FA."));

        String secret = leer(configurar(token)).get("secret").asText();
        MvcResult malo = activar(token, TotpTestUtil.codigoInvalido(secret));
        assertThat(malo.getResponse().getStatus()).isEqualTo(400);
        assertThat(leer(malo).get("message").asText()).isEqualTo(
                "Código TOTP inválido. Verifique la sincronización de hora de su dispositivo e intente de nuevo.");

        MvcResult ok = activar(token, TotpTestUtil.codigoActual(secret));
        assertThat(ok.getResponse().getStatus()).isEqualTo(200);
        JsonNode b = leer(ok);
        assertThat(LoginCaracterizacionTest.campos(b)).containsExactly("activo", "mensaje");
        assertThat(b.get("activo").asBoolean()).isTrue();
        assertThat(b.get("mensaje").asText()).isEqualTo("Autenticación de dos factores (2FA) activada con éxito.");
        assertThat(usuarioRepository.findById(ag.getId()).orElseThrow().isTotpActivado()).isTrue();
    }

    @Test
    @DisplayName("Login en 2 pasos: token 2FA_PENDING (5 min) que el filtro ignora; /auth/2fa/login emite el JWT final")
    void loginEnDosPasos() throws Exception {
        Enrolado e = enrolarAdmin();

        MvcResult paso1 = login(e.usuario().getEmail(), PASSWORD, ipUnica());
        JsonNode b1 = leer(paso1);
        assertThat(paso1.getResponse().getStatus()).isEqualTo(200);
        assertThat(b1.get("tokenTipo").asText()).isEqualTo("2FA_PENDING");
        assertThat(b1.get("expiracionMs").asLong()).isEqualTo(300_000L);
        assertThat(b1.get("usuario").get("email").asText()).isEqualTo(e.usuario().getEmail());
        String temp = b1.get("token").asText();
        JsonNode claims = objectMapper.readTree(new String(
                Base64.getUrlDecoder().decode(temp.split("\\.")[1]), StandardCharsets.UTF_8));
        assertThat(claims.get("type").asText()).isEqualTo("2FA_PENDING");
        assertThat(claims.get("exp").asLong() - claims.get("iat").asLong()).isEqualTo(300L);

        // El token temporal no sirve como sesión
        mockMvc.perform(get("/usuarios").header("Authorization", bearer(temp)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));
        mockMvc.perform(get("/auth/me").header("Authorization", bearer(temp)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Debe completar la verificación en dos factores (2FA) para acceder."));

        MvcResult paso2 = login2fa(temp, TotpTestUtil.seisDigitos(TotpTestUtil.codigoActual(e.secret())));
        assertThat(paso2.getResponse().getStatus()).isEqualTo(200);
        JsonNode b2 = leer(paso2);
        assertThat(b2.get("tokenTipo").asText()).isEqualTo("Bearer");
        assertThat(b2.get("expiracionMs").asLong()).isEqualTo(28_800_000L);
        mockMvc.perform(get("/usuarios").header("Authorization", bearer(b2.get("token").asText())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("/auth/2fa/login acepta la clave 'tempToken' (camelCase) además de 'temp_token'")
    void login2faCamelCase() throws Exception {
        Enrolado e = enrolarAdmin();
        String temp = tempToken(e.usuario());
        mockMvc.perform(post("/auth/2fa/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("tempToken", temp,
                                "codigo", TotpTestUtil.seisDigitos(TotpTestUtil.codigoActual(e.secret()))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenTipo").value("Bearer"));
    }

    @Test
    @DisplayName("/auth/2fa/login: código incorrecto, token final en vez de temporal y token basura → 401")
    void login2faErrores() throws Exception {
        Enrolado e = enrolarAdmin();
        String temp = tempToken(e.usuario());

        MvcResult malo = login2fa(temp, TotpTestUtil.seisDigitos(TotpTestUtil.codigoInvalido(e.secret())));
        assertThat(malo.getResponse().getStatus()).isEqualTo(401);
        assertThat(leer(malo).get("message").asText()).isEqualTo(MSG_CODIGO_INVALIDO);

        String finalToken = leer(login2fa(temp, TotpTestUtil.seisDigitos(TotpTestUtil.codigoActual(e.secret()))))
                .get("token").asText();
        MvcResult noTemporal = login2fa(finalToken, "123456");
        assertThat(noTemporal.getResponse().getStatus()).isEqualTo(401);
        assertThat(leer(noTemporal).get("message").asText())
                .isEqualTo("El token proporcionado no es un token temporal de segundo factor.");

        MvcResult basura = login2fa("no.es.jwt", "123456");
        assertThat(basura.getResponse().getStatus()).isEqualTo(401);
        assertThat(leer(basura).get("message").asText())
                .isEqualTo("Token temporal inválido o expirado. Inicie sesión nuevamente.");
    }

    @Test
    @DisplayName("Código de respaldo: válido funciona UNA vez (también en minúsculas); reutilizado → 401")
    void codigosDeRespaldoUnSoloUso() throws Exception {
        Enrolado e = enrolarAdmin();
        String codigo = e.backupCodes().get(2);

        MvcResult primero = login2fa(tempToken(e.usuario()), codigo.toLowerCase());
        assertThat(primero.getResponse().getStatus()).isEqualTo(200);
        assertThat(leer(primero).get("tokenTipo").asText()).isEqualTo("Bearer");

        String restantes = usuarioRepository.findById(e.usuario().getId()).orElseThrow().getTotpBackupCodes();
        assertThat(restantes.split(",")).hasSize(4).doesNotContain(codigo);

        MvcResult reuso = login2fa(tempToken(e.usuario()), codigo);
        assertThat(reuso.getResponse().getStatus()).isEqualTo(401);
        assertThat(leer(reuso).get("message").asText()).isEqualTo(MSG_CODIGO_INVALIDO);

        // Otro código de respaldo sigue valiendo
        assertThat(login2fa(tempToken(e.usuario()), e.backupCodes().get(0)).getResponse().getStatus()).isEqualTo(200);
    }

    private MvcResult login2faDesde(String ip, String tempToken, String codigo) throws Exception {
        return mockMvc.perform(desdeIp(post("/auth/2fa/login"), ip).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("temp_token", tempToken, "codigo", codigo)))).andReturn();
    }

    @Test
    @DisplayName("B-005 (revisión BL-1): un login correcto con contraseña no reinicia la fuerza bruta de un usuario con 2FA")
    void login2faCiclosDeLoginNoReinicianFuerzaBruta() throws Exception {
        Enrolado e = enrolarAdmin();
        String malo = TotpTestUtil.seisDigitos(TotpTestUtil.codigoInvalido(e.secret()));
        String ip = ipUnica();

        // Ciclo 1: login correcto + 4 códigos erróneos (no agotan el token ni bloquean)
        MvcResult paso1 = login(e.usuario().getEmail(), PASSWORD, ip);
        assertThat(leer(paso1).get("tokenTipo").asText()).isEqualTo("2FA_PENDING");
        String temp1 = leer(paso1).get("token").asText();
        for (int i = 0; i < 4; i++) {
            assertThat(login2faDesde(ip, temp1, malo).getResponse().getStatus()).isEqualTo(401);
        }

        // Ciclo 2: el login correcto ya NO pone el contador a cero; el 5.º fallo acumulado bloquea email|ip
        MvcResult paso2 = login(e.usuario().getEmail(), PASSWORD, ip);
        assertThat(paso2.getResponse().getStatus()).isEqualTo(200);
        String temp2 = leer(paso2).get("token").asText();
        MvcResult quinto = login2faDesde(ip, temp2, malo);
        assertThat(leer(quinto).get("message").asText()).isEqualTo(MSG_CODIGO_INVALIDO);

        MvcResult bloqueado2fa = login2faDesde(ip, temp2, e.backupCodes().get(0));
        assertThat(bloqueado2fa.getResponse().getStatus()).isEqualTo(401);
        assertThat(leer(bloqueado2fa).get("message").asText()).startsWith("Demasiados intentos fallidos.");
        MvcResult bloqueadoLogin = login(e.usuario().getEmail(), PASSWORD, ip);
        assertThat(bloqueadoLogin.getResponse().getStatus()).isEqualTo(401);
        assertThat(leer(bloqueadoLogin).get("message").asText()).startsWith("Demasiados intentos fallidos.");
    }

    /** Lanza las peticiones a la vez (todas esperan al mismo latch) y devuelve sus respuestas. */
    private List<MvcResult> enParalelo(List<java.util.concurrent.Callable<MvcResult>> peticiones) throws Exception {
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(peticiones.size());
        java.util.concurrent.CountDownLatch salida = new java.util.concurrent.CountDownLatch(1);
        try {
            List<java.util.concurrent.Future<MvcResult>> futuros = new ArrayList<>();
            for (java.util.concurrent.Callable<MvcResult> p : peticiones) {
                futuros.add(pool.submit(() -> {
                    salida.await();
                    return p.call();
                }));
            }
            salida.countDown();
            List<MvcResult> resultados = new ArrayList<>();
            for (java.util.concurrent.Future<MvcResult> f : futuros) {
                resultados.add(f.get(60, java.util.concurrent.TimeUnit.SECONDS));
            }
            return resultados;
        } finally {
            pool.shutdownNow();
        }
    }

    private long codigosEvaluados(List<MvcResult> resultados) throws Exception {
        long n = 0;
        for (MvcResult r : resultados) {
            if (MSG_CODIGO_INVALIDO.equals(leer(r).path("message").asText())) {
                n++;
            }
        }
        return n;
    }

    @Test
    @DisplayName("B-005 (revisión BL-2): 20 peticiones concurrentes prueban como máximo 5 códigos, por token y por email|ip")
    void login2faConcurrenteMaximoCincoCodigos() throws Exception {
        Enrolado e = enrolarAdmin();
        String malo = TotpTestUtil.seisDigitos(TotpTestUtil.codigoInvalido(e.secret()));

        // Mismo token temporal
        String ipA = ipUnica();
        String temp = tempToken(e.usuario());
        List<java.util.concurrent.Callable<MvcResult>> mismoToken = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            mismoToken.add(() -> login2faDesde(ipA, temp, malo));
        }
        List<MvcResult> r1 = enParalelo(mismoToken);
        for (MvcResult r : r1) {
            assertThat(r.getResponse().getStatus()).isEqualTo(401);
        }
        assertThat(codigosEvaluados(r1)).as("códigos evaluados con un mismo token").isEqualTo(5);

        // Tokens distintos, misma clave email|ip (otra cuenta: la primera ya consumió su límite por cuenta)
        Enrolado e2 = enrolarAdmin();
        String ipB = ipUnica();
        List<String> temps = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            temps.add(leer(login(e2.usuario().getEmail(), PASSWORD, ipB)).get("token").asText());
        }
        List<java.util.concurrent.Callable<MvcResult>> mismaIp = new ArrayList<>();
        for (String t : temps) {
            mismaIp.add(() -> login2faDesde(ipB, t, malo));
        }
        List<MvcResult> r2 = enParalelo(mismaIp);
        for (MvcResult r : r2) {
            assertThat(r.getResponse().getStatus()).isEqualTo(401);
        }
        assertThat(codigosEvaluados(r2)).as("códigos evaluados desde un mismo email|ip").isEqualTo(5);
    }

    @Test
    @DisplayName("B-005: tras 5 códigos erróneos (TOTP o respaldo) el token temporal queda invalidado y los fallos cuentan para la fuerza bruta")
    void login2faLimitadoACincoIntentos() throws Exception {
        Enrolado e = enrolarAdmin();
        String malo = TotpTestUtil.seisDigitos(TotpTestUtil.codigoInvalido(e.secret()));

        // 4 fallos no agotan el token: el código correcto todavía entra (y limpia el contador de fuerza bruta)
        String ipA = ipUnica();
        String tempA = tempToken(e.usuario());
        for (int i = 0; i < 4; i++) {
            assertThat(login2faDesde(ipA, tempA, malo).getResponse().getStatus()).isEqualTo(401);
        }
        assertThat(login2faDesde(ipA, tempA, e.backupCodes().get(0)).getResponse().getStatus()).isEqualTo(200);

        // 5 fallos (3 TOTP + 2 de respaldo) invalidan el token temporal aunque después llegue un código correcto
        String ipB = ipUnica();
        String tempB = tempToken(e.usuario());
        for (String codigo : List.of(malo, malo, malo, "ZZZZZZZZ", "YYYYYYYY")) {
            MvcResult r = login2faDesde(ipB, tempB, codigo);
            assertThat(r.getResponse().getStatus()).isEqualTo(401);
            assertThat(leer(r).get("message").asText()).isEqualTo(MSG_CODIGO_INVALIDO);
        }
        MvcResult agotado = login2faDesde(ipUnica(), tempB, e.backupCodes().get(1));
        assertThat(agotado.getResponse().getStatus()).isEqualTo(401);
        assertThat(leer(agotado).get("message").asText())
                .isEqualTo("Token temporal inválido o expirado. Inicie sesión nuevamente.");
        // El código de respaldo no se consumió con el token agotado
        assertThat(usuarioRepository.findById(e.usuario().getId()).orElseThrow().getTotpBackupCodes())
                .contains(e.backupCodes().get(1));

        // Los 5 fallos cuentan para la fuerza bruta de la misma clave email|ip que el login con contraseña
        MvcResult bloqueado = login(e.usuario().getEmail(), PASSWORD, ipB);
        assertThat(bloqueado.getResponse().getStatus()).isEqualTo(401);
        assertThat(leer(bloqueado).get("message").asText()).startsWith("Demasiados intentos fallidos.");

        // Hay que volver a empezar con la contraseña (desde una clave email|ip no bloqueada)
        String ipC = ipUnica();
        MvcResult paso1 = login(e.usuario().getEmail(), PASSWORD, ipC);
        assertThat(leer(paso1).get("tokenTipo").asText()).isEqualTo("2FA_PENDING");
        assertThat(login2faDesde(ipC, leer(paso1).get("token").asText(), e.backupCodes().get(1))
                .getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("B-013: el código TOTP (y los de pasos anteriores) y el token temporal son de un solo uso")
    void codigoTotpYTokenTemporalDeUnSoloUso() throws Exception {
        Enrolado e = enrolarAdmin();
        String temp = tempToken(e.usuario());
        long paso = System.currentTimeMillis() / 1000 / 30;
        String codigo = TotpTestUtil.seisDigitos(TotpTestUtil.codigo(e.secret(), paso));
        assertThat(login2fa(temp, codigo).getResponse().getStatus()).isEqualTo(200);

        // El mismo token temporal ya no sirve, aunque llegue otro código válido (que no se consume)
        MvcResult reusoToken = login2fa(temp, e.backupCodes().get(0));
        assertThat(reusoToken.getResponse().getStatus()).isEqualTo(401);
        assertThat(leer(reusoToken).get("message").asText())
                .isEqualTo("Token temporal inválido o expirado. Inicie sesión nuevamente.");
        assertThat(usuarioRepository.findById(e.usuario().getId()).orElseThrow().getTotpBackupCodes())
                .contains(e.backupCodes().get(0));

        // El mismo código TOTP con otro token temporal → rechazado
        MvcResult reusoCodigo = login2fa(tempToken(e.usuario()), codigo);
        assertThat(reusoCodigo.getResponse().getStatus()).isEqualTo(401);
        assertThat(leer(reusoCodigo).get("message").asText()).isEqualTo(MSG_CODIGO_INVALIDO);

        // Un código de un paso anterior al último usado → rechazado
        MvcResult anterior = login2fa(tempToken(e.usuario()),
                TotpTestUtil.seisDigitos(TotpTestUtil.codigo(e.secret(), paso - 1)));
        assertThat(anterior.getResponse().getStatus()).isEqualTo(401);

        // El código del paso siguiente (todavía no usado) sí entra
        MvcResult siguiente = login2fa(tempToken(e.usuario()), TotpTestUtil.seisDigitos(
                TotpTestUtil.codigo(e.secret(), System.currentTimeMillis() / 1000 / 30 + 1)));
        assertThat(siguiente.getResponse().getStatus()).isEqualTo(200);
    }

    private MvcResult desactivar(String token, String password, String codigo) throws Exception {
        return mockMvc.perform(post("/auth/2fa/desactivar").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("password_actual", password, "codigo", codigo)))).andReturn();
    }

    /** Cuenta de un rol sin 2FA obligatorio con el 2FA ya activo (la API solo deja configurarlo a ADMIN_GENERAL). */
    private UsuarioEntity conTotpActivo(String rol, String secret, String codigosRespaldo) {
        UsuarioEntity u = crearUsuario(rol, PASSWORD);
        u.setTotpSecret(secret);
        u.setTotpBackupCodes(codigosRespaldo);
        u.setTotpActivado(true);
        return usuarioRepository.save(u);
    }

    @Test
    @DisplayName("B-009 (a): el propio usuario desactiva su 2FA con contraseña y código; ADMIN_GENERAL no puede (política)")
    void desactivarPropio2fa() throws Exception {
        // ADMIN_GENERAL: 2FA obligatorio por política institucional → 403 aunque dé contraseña y código válidos
        Enrolado e = enrolarAdmin();
        String tokenAg = leer(login2fa(tempToken(e.usuario()), e.backupCodes().get(0))).get("token").asText();
        mockMvc.perform(post("/auth/2fa/desactivar").header("Authorization", bearer(tokenAg))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("password_actual", PASSWORD,
                                "codigo", TotpTestUtil.seisDigitos(TotpTestUtil.codigoActual(e.secret()))))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("El Administrador General tiene 2FA obligatorio por política "
                        + "institucional. Use el procedimiento de contingencia autorizado."));
        assertThat(usuarioRepository.findById(e.usuario().getId()).orElseThrow().isTotpActivado()).isTrue();

        // Otro rol sin 2FA activo → 400
        UsuarioEntity sin2fa = crearUsuario("RECEPCION", PASSWORD);
        MvcResult noActivo = desactivar(tokenDe(sin2fa, PASSWORD), PASSWORD, "123456");
        assertThat(noActivo.getResponse().getStatus()).isEqualTo(400);
        assertThat(leer(noActivo).get("message").asText()).isEqualTo("El 2FA no se encuentra activo.");

        // Otro rol con 2FA activo: contraseña o código incorrectos → 400 sin cambios; ambos correctos → desactivado
        String secret = "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP";
        UsuarioEntity od = conTotpActivo("ODONTOLOGO", secret, "AAAA2222,BBBB3333");
        MvcResult paso1 = login(od.getEmail(), PASSWORD, ipUnica());
        assertThat(leer(paso1).get("tokenTipo").asText()).isEqualTo("2FA_PENDING");
        String tokenOd = leer(login2fa(leer(paso1).get("token").asText(), "AAAA2222")).get("token").asText();
        String codigo = TotpTestUtil.seisDigitos(TotpTestUtil.codigoActual(secret));

        MvcResult passMala = desactivar(tokenOd, "no-es-la-clave", codigo);
        assertThat(passMala.getResponse().getStatus()).isEqualTo(400);
        assertThat(leer(passMala).get("message").asText()).isEqualTo(MSG_DESACTIVAR_INVALIDO);
        MvcResult codigoMalo = desactivar(tokenOd, PASSWORD, TotpTestUtil.seisDigitos(TotpTestUtil.codigoInvalido(secret)));
        assertThat(codigoMalo.getResponse().getStatus()).isEqualTo(400);
        assertThat(leer(codigoMalo).get("message").asText()).isEqualTo(MSG_DESACTIVAR_INVALIDO);
        assertThat(usuarioRepository.findById(od.getId()).orElseThrow().isTotpActivado()).isTrue();

        MvcResult ok = desactivar(tokenOd, PASSWORD, codigo);
        assertThat(ok.getResponse().getStatus()).isEqualTo(200);
        assertThat(LoginCaracterizacionTest.campos(leer(ok))).containsExactly("activo", "mensaje");
        assertThat(leer(ok).get("activo").asBoolean()).isFalse();
        UsuarioEntity enBd = usuarioRepository.findById(od.getId()).orElseThrow();
        assertThat(enBd.isTotpActivado()).isFalse();
        assertThat(enBd.getTotpSecret()).isNull();
        assertThat(enBd.getTotpBackupCodes()).isNull();
        assertThat(leer(login(od.getEmail(), PASSWORD, ipUnica())).get("tokenTipo").asText()).isEqualTo("Bearer");

        // También con un código de respaldo
        UsuarioEntity re = conTotpActivo("RECEPCION", secret, "CCCC4444,DDDD5555");
        String tokenRe = leer(login2fa(tempToken(re), "CCCC4444")).get("token").asText();
        assertThat(desactivar(tokenRe, PASSWORD, "DDDD5555").getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("B-009 (b): un ADMIN_GENERAL restablece el 2FA de OTRO usuario (no el suyo), auditado; sus sesiones caen")
    void adminRestablece2faDeOtro() throws Exception {
        UsuarioEntity admin = crearUsuario("ADMIN_GENERAL", PASSWORD);
        String tokenAdmin = tokenDe(admin, PASSWORD);
        Enrolado e = enrolarAdmin();
        String tokenVictima = leer(login2fa(tempToken(e.usuario()), e.backupCodes().get(0))).get("token").asText();
        mockMvc.perform(get("/usuarios").header("Authorization", bearer(tokenVictima))).andExpect(status().isOk());

        MvcResult r = mockMvc.perform(post("/usuarios/" + e.usuario().getId() + "/2fa/restablecer")
                .header("Authorization", bearer(tokenAdmin))).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        assertThat(LoginCaracterizacionTest.campos(leer(r))).containsExactly("mensaje", "status");
        assertThat(leer(r).get("status").asInt()).isEqualTo(200);
        assertThat(leer(r).get("mensaje").asText()).isEqualTo("2FA restablecido correctamente para " + e.usuario().getEmail());

        UsuarioEntity enBd = usuarioRepository.findById(e.usuario().getId()).orElseThrow();
        assertThat(enBd.isTotpActivado()).isFalse();
        assertThat(enBd.getTotpSecret()).isNull();
        assertThat(enBd.getTotpBackupCodes()).isNull();
        assertThat(enBd.getTokenVersion()).isEqualTo(1);
        mockMvc.perform(get("/usuarios").header("Authorization", bearer(tokenVictima))).andExpect(status().isUnauthorized());
        MvcResult relogin = login(e.usuario().getEmail(), PASSWORD, ipUnica());
        assertThat(leer(relogin).get("tokenTipo").asText()).isEqualTo("Bearer");
        assertThat(leer(relogin).get("requiereConfigurar2fa").asBoolean()).isTrue();

        // Auditoría sin secretos
        Map<String, Object> fila = jdbc.queryForMap("SELECT usuario_id, modulo, accion, tabla, detalle FROM auditoria_log "
                + "WHERE accion = 'RESTABLECER_2FA' AND registro_id = ?", e.usuario().getId().toString());
        assertThat(fila.get("usuario_id").toString()).isEqualTo(admin.getId().toString());
        assertThat(fila.get("modulo")).isEqualTo("USUARIOS");
        assertThat(fila.get("tabla")).isEqualTo("usuarios");
        assertThat(fila.get("detalle").toString()).doesNotContain(e.secret()).doesNotContain(e.backupCodes().get(1));

        // Nunca sobre su propia cuenta
        MvcResult propio = mockMvc.perform(post("/usuarios/" + admin.getId() + "/2fa/restablecer")
                .header("Authorization", bearer(tokenAdmin))).andReturn();
        assertThat(propio.getResponse().getStatus()).isEqualTo(400);
        assertThat(leer(propio).get("message").asText())
                .isEqualTo("No puede restablecer su propio 2FA. Use /auth/2fa/configurar con un código válido.");

        // Ningún otro rol
        UsuarioEntity adminSede = crearUsuario("ADMIN_SEDE", PASSWORD);
        mockMvc.perform(post("/usuarios/" + e.usuario().getId() + "/2fa/restablecer")
                        .header("Authorization", bearer(tokenDe(adminSede, PASSWORD))))
                .andExpect(status().isForbidden());
    }

    /** Cuerpo JSON sin el campo timestamp (cambia en cada respuesta). */
    private com.fasterxml.jackson.databind.node.ObjectNode sinTimestamp(MvcResult r) throws Exception {
        com.fasterxml.jackson.databind.node.ObjectNode n = (com.fasterxml.jackson.databind.node.ObjectNode) leer(r);
        n.remove("timestamp");
        return n;
    }

    @Test
    @DisplayName("B-010 (revisión BL-3): 5 códigos erróneos en configurar bloquean la operación 15 min, incluso con un código válido")
    void configurarConFuerzaBrutaSeBloquea() throws Exception {
        Enrolado e = enrolarAdmin();
        String tokenAg = leer(login2fa(tempToken(e.usuario()), e.backupCodes().get(0))).get("token").asText();
        String malo = TotpTestUtil.seisDigitos(TotpTestUtil.codigoInvalido(e.secret()));
        for (int i = 0; i < 5; i++) {
            MvcResult r = configurarConCodigo(tokenAg, malo);
            assertThat(r.getResponse().getStatus()).isEqualTo(400);
            assertThat(leer(r).get("message").asText()).isEqualTo(MSG_CODIGO_INVALIDO);
        }

        MvcResult bloqueado = configurarConCodigo(tokenAg, e.backupCodes().get(1));
        assertThat(bloqueado.getResponse().getStatus()).isEqualTo(429);
        assertThat(leer(bloqueado).get("message").asText()).startsWith("Demasiados intentos fallidos de verificación 2FA.");
        UsuarioEntity enBd = usuarioRepository.findById(e.usuario().getId()).orElseThrow();
        assertThat(enBd.isTotpActivado()).isTrue();
        assertThat(enBd.getTotpSecret()).isEqualTo(e.secret());
        assertThat(enBd.getTotpBackupCodes()).contains(e.backupCodes().get(1)); // el código de respaldo no se consumió
    }

    @Test
    @DisplayName("B-009a (revisión BL-3): desactivar responde igual ante contraseña o código incorrectos y comparte el límite de 5 fallos")
    void desactivarSinOraculoYConLimite() throws Exception {
        String secret = "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP";
        UsuarioEntity od = conTotpActivo("ODONTOLOGO", secret, "EEEE6666,FFFF7777");
        String token = leer(login2fa(tempToken(od), "EEEE6666")).get("token").asText();
        String codigoBueno = TotpTestUtil.seisDigitos(TotpTestUtil.codigoActual(secret));

        MvcResult passMala = desactivar(token, "no-es-la-clave", codigoBueno);
        MvcResult codigoMalo = desactivar(token, PASSWORD, TotpTestUtil.seisDigitos(TotpTestUtil.codigoInvalido(secret)));
        assertThat(passMala.getResponse().getStatus()).isEqualTo(400);
        assertThat(codigoMalo.getResponse().getStatus()).isEqualTo(passMala.getResponse().getStatus());
        assertThat(codigoMalo.getResponse().getContentType()).isEqualTo(passMala.getResponse().getContentType());
        assertThat(sinTimestamp(codigoMalo)).isEqualTo(sinTimestamp(passMala));
        assertThat(leer(passMala).get("message").asText()).isEqualTo(MSG_DESACTIVAR_INVALIDO);

        for (int i = 0; i < 3; i++) {
            assertThat(desactivar(token, "otra-mala-" + i, codigoBueno).getResponse().getStatus()).isEqualTo(400);
        }
        // 5 fallos: bloqueado aunque la contraseña y el código sean correctos
        MvcResult bloqueado = desactivar(token, PASSWORD, codigoBueno);
        assertThat(bloqueado.getResponse().getStatus()).isEqualTo(429);
        assertThat(leer(bloqueado).get("message").asText()).startsWith("Demasiados intentos fallidos de verificación 2FA.");
        assertThat(usuarioRepository.findById(od.getId()).orElseThrow().isTotpActivado()).isTrue();
    }

    private MvcResult configurarConCodigo(String token, String codigo) throws Exception {
        return mockMvc.perform(post("/auth/2fa/configurar").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("codigo", codigo)))).andReturn();
    }

    @Test
    @DisplayName("B-010: con 2FA activo, configurar exige un código válido (TOTP o respaldo); sin él → 400 y no cambia nada")
    void reconfigurarExigeCodigo() throws Exception {
        Enrolado e = enrolarAdmin();
        String temp = tempToken(e.usuario());
        String tokenAg = leer(login2fa(temp, TotpTestUtil.seisDigitos(TotpTestUtil.codigoActual(e.secret()))))
                .get("token").asText();
        String codigosAntes = usuarioRepository.findById(e.usuario().getId()).orElseThrow().getTotpBackupCodes();

        MvcResult sinCodigo = configurar(tokenAg);
        MvcResult codigoMalo = configurarConCodigo(tokenAg, TotpTestUtil.seisDigitos(TotpTestUtil.codigoInvalido(e.secret())));
        MvcResult respaldoMalo = configurarConCodigo(tokenAg, "ZZZZZZZZ");
        for (MvcResult r : List.of(sinCodigo, codigoMalo, respaldoMalo)) {
            assertThat(r.getResponse().getStatus()).isEqualTo(400);
            assertThat(leer(r).get("message").asText()).isEqualTo(MSG_CODIGO_INVALIDO);
        }
        UsuarioEntity enBd = usuarioRepository.findById(e.usuario().getId()).orElseThrow();
        assertThat(enBd.isTotpActivado()).isTrue();
        assertThat(enBd.getTotpSecret()).isEqualTo(e.secret());
        assertThat(enBd.getTotpBackupCodes()).isEqualTo(codigosAntes);
        assertThat(leer(login(e.usuario().getEmail(), PASSWORD, ipUnica())).get("tokenTipo").asText())
                .isEqualTo("2FA_PENDING");

        // Con un código de respaldo válido sigue el flujo actual: secreto nuevo pendiente de verificar
        MvcResult conRespaldo = configurarConCodigo(tokenAg, e.backupCodes().get(3));
        assertThat(conRespaldo.getResponse().getStatus()).isEqualTo(200);
        assertThat(leer(conRespaldo).get("secret").asText()).isNotEqualTo(e.secret());
        assertThat(usuarioRepository.findById(e.usuario().getId()).orElseThrow().isTotpActivado()).isFalse();

        // También vale un código TOTP válido (el de la ventana siguiente, aún no usado)
        Enrolado otro = enrolarAdmin();
        String tokenOtro = leer(login2fa(tempToken(otro.usuario()), otro.backupCodes().get(0))).get("token").asText();
        MvcResult conTotp = configurarConCodigo(tokenOtro, TotpTestUtil.seisDigitos(
                TotpTestUtil.codigo(otro.secret(), System.currentTimeMillis() / 1000 / 30 + 1)));
        assertThat(conTotp.getResponse().getStatus()).isEqualTo(200);
        assertThat(leer(conTotp).get("secret").asText()).isNotEqualTo(otro.secret());
    }

    @Test
    @DisplayName("configurar y verificar-activar solo para ADMIN_GENERAL: otros roles → 403 'Access Denied'")
    void soloAdminGeneralConfigura() throws Exception {
        UsuarioEntity re = crearUsuario("RECEPCION", PASSWORD);
        String token = tokenDe(re, PASSWORD);
        mockMvc.perform(post("/auth/2fa/configurar").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value("Access Denied"));
        mockMvc.perform(post("/auth/2fa/verificar-activar").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"codigo\":123456}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("B-008 (revisión obs. 10): un token temporal emitido antes de cambiar la contraseña ya no completa el 2FA")
    void tokenTemporalAnteriorACambioDePasswordRechazado() throws Exception {
        Enrolado e = enrolarAdmin();
        String tempViejo = tempToken(e.usuario());
        String sesion = leer(login2fa(tempToken(e.usuario()), e.backupCodes().get(0))).get("token").asText();
        assertThat(mockMvc.perform(post("/auth/cambiar-password").header("Authorization", bearer(sesion))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("password_actual", PASSWORD, "password_nuevo", "Cambiada-2FA-1"))))
                .andReturn().getResponse().getStatus()).isEqualTo(200);

        MvcResult r = login2fa(tempViejo, e.backupCodes().get(1));
        assertThat(r.getResponse().getStatus()).isEqualTo(401);
        assertThat(leer(r).get("message").asText()).isEqualTo("Token temporal inválido o expirado. Inicie sesión nuevamente.");
        assertThat(usuarioRepository.findById(e.usuario().getId()).orElseThrow().getTotpBackupCodes())
                .contains(e.backupCodes().get(1));
    }

    @Test
    @DisplayName("B-009b (revisión obs. 10): restablecer el 2FA de un id inexistente → 400, como el resto de /usuarios")
    void restablecer2faIdInexistente() throws Exception {
        UsuarioEntity admin = crearUsuario("ADMIN_GENERAL", PASSWORD);
        java.util.UUID inexistente = java.util.UUID.randomUUID();
        MvcResult r = mockMvc.perform(post("/usuarios/" + inexistente + "/2fa/restablecer")
                .header("Authorization", bearer(tokenDe(admin, PASSWORD)))).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        assertThat(leer(r).get("message").asText()).isEqualTo("Usuario no encontrado con ID: " + inexistente);
    }

    @Test
    @DisplayName("Revisión 2 (obs. 3): 10 códigos erróneos de una cuenta desde varias IP bloquean su 2FA, incluso con un código válido")
    void login2faLimitePorCuentaConVariasIps() throws Exception {
        Enrolado e = enrolarAdmin();
        String malo = TotpTestUtil.seisDigitos(TotpTestUtil.codigoInvalido(e.secret()));
        for (int ip = 0; ip < 2; ip++) {
            String origen = ipUnica();
            String temp = tempToken(e.usuario());
            for (int i = 0; i < 5; i++) {
                MvcResult r = login2faDesde(origen, temp, malo);
                assertThat(leer(r).get("message").asText()).isEqualTo(MSG_CODIGO_INVALIDO);
            }
        }

        // Tercera IP, token nuevo y código de respaldo válido: bloqueado por la cuenta, y el código no se consume
        MvcResult bloqueado = login2faDesde(ipUnica(), tempToken(e.usuario()), e.backupCodes().get(0));
        assertThat(bloqueado.getResponse().getStatus()).isEqualTo(401);
        assertThat(leer(bloqueado).get("message").asText()).startsWith("Demasiados intentos fallidos.");
        assertThat(usuarioRepository.findById(e.usuario().getId()).orElseThrow().getTotpBackupCodes())
                .contains(e.backupCodes().get(0));
    }

    @Test
    @DisplayName("Revisión 2 (obs. 6): desactivar con la contraseña incorrecta NO consume el código de respaldo enviado")
    void desactivarConPasswordMalaNoConsumeRespaldo() throws Exception {
        UsuarioEntity od = conTotpActivo("ODONTOLOGO", "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP", "GGGG2222,HHHH3333");
        String token = leer(login2fa(tempToken(od), "GGGG2222")).get("token").asText();

        MvcResult passMala = desactivar(token, "no-es-la-clave", "HHHH3333");
        assertThat(passMala.getResponse().getStatus()).isEqualTo(400);
        assertThat(usuarioRepository.findById(od.getId()).orElseThrow().getTotpBackupCodes()).contains("HHHH3333");

        assertThat(desactivar(token, PASSWORD, "HHHH3333").getResponse().getStatus()).isEqualTo(200);
    }
}
