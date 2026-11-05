package com.odontomax.identidad.infrastructure.adapter;

import com.odontomax.identidad.application.port.out.BruteForceProtectionPort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Contador de intentos fallidos de login en memoria (por instancia). 5 fallos bloquean la clave
 * {@code email|ip} 15 minutos contados desde el primer fallo de la ventana.
 */
@Component
public class InMemoryBruteForceAdapter implements BruteForceProtectionPort {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCK_TIME_DURATION_MS = 15 * 60 * 1000;
    private static final int MAX_ENTRADAS_POR_DEFECTO = 10_000;

    private final Map<String, AttemptTracker> loginAttempts = new ConcurrentHashMap<>();
    private final int maxEntradas;

    public InMemoryBruteForceAdapter() {
        this(MAX_ENTRADAS_POR_DEFECTO);
    }

    InMemoryBruteForceAdapter(int maxEntradas) {
        this.maxEntradas = maxEntradas;
    }

    @Override
    public OptionalLong minutosDeBloqueoRestantes(String key) {
        AttemptTracker tracker = loginAttempts.get(key);
        if (tracker != null) {
            long now = Instant.now().toEpochMilli();
            if (tracker.attempts >= MAX_FAILED_ATTEMPTS) {
                if (now - tracker.firstAttemptTime < LOCK_TIME_DURATION_MS) {
                    long minutesLeft = (LOCK_TIME_DURATION_MS - (now - tracker.firstAttemptTime)) / 60000;
                    return OptionalLong.of(Math.max(1, minutesLeft));
                } else {
                    loginAttempts.remove(key);
                }
            }
        }
        return OptionalLong.empty();
    }

    @Override
    public void recordFailedAttempt(String key) {
        if (!hayHueco(key)) {
            return;
        }
        long now = Instant.now().toEpochMilli();
        loginAttempts.compute(key, (k, v) -> {
            if (v == null || (now - v.firstAttemptTime > LOCK_TIME_DURATION_MS)) {
                return new AttemptTracker(1, now);
            }
            return new AttemptTracker(v.attempts + 1, v.firstAttemptTime);
        });
    }

    @Override
    public void resetAttempts(String key) {
        loginAttempts.remove(key);
    }

    @Override
    public OptionalLong reservarIntento(String key) {
        return reservarIntento(key, MAX_FAILED_ATTEMPTS);
    }

    @Override
    public OptionalLong reservarIntento(String key, int maximo) {
        if (!hayHueco(key)) {
            return OptionalLong.of(Math.max(1, LOCK_TIME_DURATION_MS / 60000));
        }
        long now = Instant.now().toEpochMilli();
        long[] minutosBloqueo = {-1};
        loginAttempts.compute(key, (k, v) -> {
            if (v == null || now - v.firstAttemptTime > LOCK_TIME_DURATION_MS) {
                return new AttemptTracker(1, now);
            }
            if (v.attempts >= maximo) {
                minutosBloqueo[0] = Math.max(1, (LOCK_TIME_DURATION_MS - (now - v.firstAttemptTime)) / 60000);
                return v;
            }
            return new AttemptTracker(v.attempts + 1, v.firstAttemptTime);
        });
        return minutosBloqueo[0] >= 0 ? OptionalLong.of(minutosBloqueo[0]) : OptionalLong.empty();
    }

    private boolean hayHueco(String key) {
        if (loginAttempts.containsKey(key) || loginAttempts.size() < maxEntradas) {
            return true;
        }
        purgeExpiredAttempts();
        return loginAttempts.size() < maxEntradas;
    }

    @Scheduled(fixedRate = 60000)
    public void purgeExpiredAttempts() {
        long now = Instant.now().toEpochMilli();
        loginAttempts.entrySet().removeIf(entry -> (now - entry.getValue().firstAttemptTime > LOCK_TIME_DURATION_MS));
    }

    private record AttemptTracker(int attempts, long firstAttemptTime) {}
}
