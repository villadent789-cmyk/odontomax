package com.odontomax.identidad.caracterizacion;

import com.odontomax.identidad.infrastructure.persistence.entity.UsuarioEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Protección anti fuerza bruta de POST /auth/login (AuthService L115-119, L551-582).
 *
 * Comportamiento fijado (actual):
 * - Clave = email (minúsculas) + "|" + IP (getRemoteAddr). No es por usuario ni solo por IP.
 * - Los 5 primeros fallos devuelven "credenciales inválidas"; el bloqueo se aplica a partir del 6.º intento.
 * - Durante el bloqueo se rechaza incluso la contraseña correcta.
 * - La ventana de 15 min cuenta desde el PRIMER fallo, no desde el bloqueo.
 * - Un login correcto borra el contador.
 */
@Testcontainers(disabledWithoutDocker = true)
class BruteForceCaracterizacionTest extends IdentidadPostgresTestBase {

    static final String REGEX_BLOQUEO =
            "Demasiados intentos fallidos\\. Cuenta temporalmente bloqueada\\. Intente de nuevo en \\d+ minutos\\.";
    static final long MINUTO = 60_000L;

    private void fallar(String email, String ip, int veces) throws Exception {
        for (int i = 1; i <= veces; i++) {
            MvcResult r = login(email, "clave-incorrecta", ip);
            assertThat(r.getResponse().getStatus()).as("intento %d", i).isEqualTo(401);
            assertThat(leer(r).get("message").asText()).as("intento %d", i)
                    .isEqualTo(LoginCaracterizacionTest.MSG_CREDENCIALES);
        }
    }

    private String mensaje(MvcResult r) throws Exception {
        return leer(r).get("message").asText();
    }

    @Test
    @DisplayName("Fallos 1-5 → credenciales inválidas; 6.º intento (aun con la clave correcta) → 401 de bloqueo")
    void bloqueoTrasCincoFallos() throws Exception {
        UsuarioEntity u = crearUsuario("RECEPCION", PASSWORD);
        String ip = ipUnica();

        fallar(u.getEmail(), ip, 5);

        MvcResult sexto = login(u.getEmail(), PASSWORD, ip);
        assertThat(sexto.getResponse().getStatus()).isEqualTo(401);
        assertThat(mensaje(sexto)).matches(REGEX_BLOQUEO);
        assertThat(mensaje(sexto)).endsWith("en 14 minutos.");
        assertThat(leer(sexto).get("error").asText()).isEqualTo("Unauthorized");
    }

    @Test
    @DisplayName("La clave es email|ip: misma cuenta desde otra IP entra; otra cuenta desde la misma IP también")
    void claveEsEmailMasIp() throws Exception {
        UsuarioEntity victima = crearUsuario("ODONTOLOGO", PASSWORD);
        UsuarioEntity otra = crearUsuario("ODONTOLOGO", PASSWORD);
        String ipAtacante = ipUnica();

        fallar(victima.getEmail(), ipAtacante, 5);
        assertThat(mensaje(login(victima.getEmail(), PASSWORD, ipAtacante))).matches(REGEX_BLOQUEO);

        // No es por usuario: la víctima entra desde otra IP
        assertThat(login(victima.getEmail(), PASSWORD, ipUnica()).getResponse().getStatus()).isEqualTo(200);
        // No es por IP: otra cuenta entra desde la IP bloqueada
        assertThat(login(otra.getEmail(), PASSWORD, ipAtacante).getResponse().getStatus()).isEqualTo(200);
        assertThat(EstadoEnMemoriaTestSupport.hayIntentosRegistrados(jdbc,
                victima.getEmail() + "|" + ipAtacante)).isTrue();
    }

    @Test
    @DisplayName("Un login correcto reinicia el contador (4 fallos + éxito + 4 fallos → no hay bloqueo)")
    void loginCorrectoReiniciaContador() throws Exception {
        UsuarioEntity u = crearUsuario("ADMIN_SEDE", PASSWORD);
        String ip = ipUnica();

        fallar(u.getEmail(), ip, 4);
        assertThat(login(u.getEmail(), PASSWORD, ip).getResponse().getStatus()).isEqualTo(200);
        assertThat(EstadoEnMemoriaTestSupport.hayIntentosRegistrados(jdbc, u.getEmail() + "|" + ip)).isFalse();

        fallar(u.getEmail(), ip, 4);
        assertThat(login(u.getEmail(), PASSWORD, ip).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Correos inexistentes y cuentas inactivas también suman intentos y se bloquean igual")
    void inexistenteEInactivoCuentan() throws Exception {
        String ip = ipUnica();
        String inexistente = emailUnico("fantasma");
        fallar(inexistente, ip, 5);
        assertThat(mensaje(login(inexistente, PASSWORD, ip))).matches(REGEX_BLOQUEO);

        UsuarioEntity inactivo = crearUsuario(emailUnico("inactivo"), "RECEPCION", PASSWORD, false);
        for (int i = 0; i < 5; i++) {
            assertThat(mensaje(login(inactivo.getEmail(), PASSWORD, ip)))
                    .isEqualTo(LoginCaracterizacionTest.MSG_CREDENCIALES);
        }
        assertThat(mensaje(login(inactivo.getEmail(), PASSWORD, ip))).matches(REGEX_BLOQUEO);
    }

    @Test
    @DisplayName("El bloqueo expira a los 15 min contados desde el PRIMER fallo (reloj simulado)")
    void expiracionDelBloqueo() throws Exception {
        UsuarioEntity u = crearUsuario("RECEPCION", PASSWORD);
        String ip = ipUnica();
        String clave = u.getEmail() + "|" + ip;
        fallar(u.getEmail(), ip, 5);

        assertThat(EstadoEnMemoriaTestSupport.envejecerIntentosLogin(jdbc, clave, 14 * MINUTO)).isTrue();
        MvcResult aLos14 = login(u.getEmail(), PASSWORD, ip);
        assertThat(aLos14.getResponse().getStatus()).isEqualTo(401);
        assertThat(mensaje(aLos14)).endsWith("Intente de nuevo en 1 minutos.");

        EstadoEnMemoriaTestSupport.envejecerIntentosLogin(jdbc, clave, MINUTO + 1_000L);
        assertThat(login(u.getEmail(), PASSWORD, ip).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Fallos repartidos: si el 1.º fallo fue hace 14 min, el bloqueo tras el 5.º dura solo ~1 min")
    void ventanaDesdeElPrimerFallo() throws Exception {
        UsuarioEntity u = crearUsuario("ODONTOLOGO", PASSWORD);
        String ip = ipUnica();
        String clave = u.getEmail() + "|" + ip;

        fallar(u.getEmail(), ip, 4);
        EstadoEnMemoriaTestSupport.envejecerIntentosLogin(jdbc, clave, 14 * MINUTO);
        fallar(u.getEmail(), ip, 1);

        MvcResult bloqueado = login(u.getEmail(), PASSWORD, ip);
        assertThat(mensaje(bloqueado)).endsWith("Intente de nuevo en 1 minutos.");

        EstadoEnMemoriaTestSupport.envejecerIntentosLogin(jdbc, clave, MINUTO + 1_000L);
        assertThat(login(u.getEmail(), PASSWORD, ip).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Si pasan más de 15 min entre fallos, el contador vuelve a 1 (no se acumulan)")
    void fallosAntiguosNoSeAcumulan() throws Exception {
        UsuarioEntity u = crearUsuario("RECEPCION", PASSWORD);
        String ip = ipUnica();
        String clave = u.getEmail() + "|" + ip;

        fallar(u.getEmail(), ip, 4);
        EstadoEnMemoriaTestSupport.envejecerIntentosLogin(jdbc, clave, 15 * MINUTO + 1_000L);
        fallar(u.getEmail(), ip, 4);
        assertThat(login(u.getEmail(), PASSWORD, ip).getResponse().getStatus()).isEqualTo(200);
    }
}
