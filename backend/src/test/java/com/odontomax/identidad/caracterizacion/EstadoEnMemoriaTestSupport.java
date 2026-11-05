package com.odontomax.identidad.caracterizacion;

import com.odontomax.identidad.api.ClinicaUserPrincipal;
import com.odontomax.identidad.application.port.out.RegistroAuditoriaPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.Map;

/**
 * Único punto de la suite que toca el estado interno en memoria de identidad.
 *
 * El código actual no tiene reloj inyectable: la fuerza bruta usa {@code Instant.now()} y la caché JWT
 * {@code System.currentTimeMillis()}. Para fijar las ventanas de 15 min y 60 s sin esperas reales,
 * estos métodos "envejecen" las entradas restando milisegundos a su marca de tiempo por reflexión.
 *
 * Cuando el plan (secciones 3.1 y 3.2, pasos P5 y P9) mueva ese estado a
 * {@code InMemoryBruteForceAdapter} y {@code InMemoryJwtUserCacheAdapter}, SOLO hay que adaptar esta
 * clase (nombre del bean/campo), no los tests que la usan.
 */
final class EstadoEnMemoriaTestSupport {

    private EstadoEnMemoriaTestSupport() {
    }

    /** Resta {@code ms} a {@code primer_intento} del contador persistido de la clave {@code email|ip}. */
    static boolean envejecerIntentosLogin(JdbcTemplate jdbc, String clave, long ms) {
        return jdbc.update("UPDATE seguridad_login_intentos SET primer_intento = primer_intento - ? WHERE clave = ?", ms, clave) == 1;
    }

    /** ¿Existe contador de intentos para la clave? */
    static boolean hayIntentosRegistrados(JdbcTemplate jdbc, String clave) {
        return jdbc.queryForObject("SELECT count(*) FROM seguridad_login_intentos WHERE clave = ?", Integer.class, clave) > 0;
    }

    /** Resta {@code ms} a {@code cachedAt} de la entrada de la caché JWT del email (en minúsculas). */
    static boolean envejecerCacheJwt(Object jwtCacheBean, String emailLower, long ms) {
        Object target = AopTestUtils.getUltimateTargetObject(jwtCacheBean);
        @SuppressWarnings("unchecked")
        Map<String, Object> mapa = (Map<String, Object>) ReflectionTestUtils.getField(target, "userCache");
        return envejecerEntrada(mapa, emailLower, "cachedAt", ms);
    }

    static boolean hayEntradaEnCacheJwt(Object jwtCacheBean, String emailLower) {
        Object target = AopTestUtils.getUltimateTargetObject(jwtCacheBean);
        Map<?, ?> mapa = (Map<?, ?>) ReflectionTestUtils.getField(target, "userCache");
        return mapa.containsKey(emailLower);
    }

    /**
     * Escribe un evento de seguridad con el adaptador de auditoría de identidad (antes, método privado
     * {@code registrarAuditoria} de AuthService). No hay ruta HTTP que lo invoque con usuario null.
     */
    static void registrarAuditoriaAuthService(Object registroAuditoriaBean, Object principal, String accion, String detalle) {
        ((RegistroAuditoriaPort) registroAuditoriaBean).registrarEventoSeguridad((ClinicaUserPrincipal) principal, accion, detalle);
    }

    private static boolean envejecerEntrada(Map<String, Object> mapa, String clave, String campoTiempo, long ms) {
        Object actual = mapa.get(clave);
        if (actual == null) {
            return false;
        }
        try {
            Class<?> tipo = actual.getClass();
            RecordComponent[] componentes = tipo.getRecordComponents();
            Object[] valores = new Object[componentes.length];
            Class<?>[] tipos = new Class<?>[componentes.length];
            for (int i = 0; i < componentes.length; i++) {
                var accessor = componentes[i].getAccessor();
                accessor.setAccessible(true);
                Object v = accessor.invoke(actual);
                if (componentes[i].getName().equals(campoTiempo)) {
                    v = ((Long) v) - ms;
                }
                valores[i] = v;
                tipos[i] = componentes[i].getType();
            }
            Constructor<?> ctor = tipo.getDeclaredConstructor(tipos);
            ctor.setAccessible(true);
            mapa.put(clave, ctor.newInstance(valores));
            return true;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("No se pudo envejecer la entrada " + clave, e);
        }
    }
}
