package com.odontomax.identidad.infrastructure.adapter;

import com.odontomax.identidad.application.port.out.BruteForceProtectionPort;
import com.odontomax.identidad.infrastructure.persistence.entity.LoginIntentoEntity;
import com.odontomax.identidad.infrastructure.persistence.repository.SpringDataLoginIntentoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.OptionalLong;

@Component
@Primary
@RequiredArgsConstructor
public class JpaBruteForceAdapter implements BruteForceProtectionPort {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCK_TIME_DURATION_MS = 15 * 60 * 1000;

    private final SpringDataLoginIntentoRepository repository;

    @Override
    @Transactional(readOnly = true)
    public OptionalLong minutosDeBloqueoRestantes(String key) {
        Optional<LoginIntentoEntity> opt = repository.findById(key);
        if (opt.isPresent()) {
            LoginIntentoEntity entity = opt.get();
            long now = Instant.now().toEpochMilli();
            if (entity.getIntentos() >= MAX_FAILED_ATTEMPTS) {
                if (now - entity.getPrimerIntento() < LOCK_TIME_DURATION_MS) {
                    long minutesLeft = (LOCK_TIME_DURATION_MS - (now - entity.getPrimerIntento())) / 60000;
                    return OptionalLong.of(Math.max(1, minutesLeft));
                }
            }
        }
        return OptionalLong.empty();
    }

    @Override
    @Transactional
    public void recordFailedAttempt(String key) {
        long now = Instant.now().toEpochMilli();
        Instant expira = Instant.ofEpochMilli(now + LOCK_TIME_DURATION_MS);
        repository.sumarIntento(key, now, LOCK_TIME_DURATION_MS, Integer.MAX_VALUE, expira);
    }

    @Override
    @Transactional
    public void resetAttempts(String key) {
        repository.deleteById(key);
    }

    @Override
    @Transactional
    public OptionalLong reservarIntento(String key) {
        return reservarIntento(key, MAX_FAILED_ATTEMPTS);
    }

    @Override
    @Transactional
    public OptionalLong reservarIntento(String key, int maximo) {
        long now = Instant.now().toEpochMilli();
        Instant expira = Instant.ofEpochMilli(now + LOCK_TIME_DURATION_MS);
        if (repository.sumarIntento(key, now, LOCK_TIME_DURATION_MS, maximo, expira) == 1) {
            return OptionalLong.empty();
        }
        long primerIntento = repository.findById(key).map(LoginIntentoEntity::getPrimerIntento).orElse(now);
        return OptionalLong.of(Math.max(1, (LOCK_TIME_DURATION_MS - (now - primerIntento)) / 60000));
    }

    @Scheduled(fixedRate = 60000)
    @Transactional
    public void purgeExpiredAttempts() {
        repository.deleteCaducados(Instant.now());
    }
}
