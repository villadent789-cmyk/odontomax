package com.odontomax.identidad.infrastructure.adapter;

import com.odontomax.identidad.application.port.out.Login2faPendientePort;
import com.odontomax.identidad.infrastructure.persistence.entity.Login2faPendienteEntity;
import com.odontomax.identidad.infrastructure.persistence.repository.SpringDataLogin2faPendienteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Component
@Primary
@RequiredArgsConstructor
public class JpaLogin2faPendienteAdapter implements Login2faPendientePort {

    private static final long RETENCION_MS = 5 * 60 * 1000L;

    private final SpringDataLogin2faPendienteRepository repository;

    @Override
    @Transactional
    public boolean intentoRechazado(String clave, int maximo) {
        long ahora = System.currentTimeMillis();
        return repository.sumarIntento(clave, ahora, maximo, Instant.ofEpochMilli(ahora + RETENCION_MS)) == 0;
    }

    @Override
    @Transactional(readOnly = true)
    public int intentosReservados(String clave) {
        return repository.findById(clave)
                .map(Login2faPendienteEntity::getIntentos)
                .orElse(0);
    }

    @Override
    @Transactional
    public synchronized boolean invalidar(String clave) {
        long ahora = System.currentTimeMillis();
        Instant expira = Instant.ofEpochMilli(ahora + RETENCION_MS);
        Optional<Login2faPendienteEntity> opt = repository.findById(clave);

        if (opt.isEmpty()) {
            repository.save(new Login2faPendienteEntity(clave, 0, true, ahora, expira));
            return false;
        }

        Login2faPendienteEntity actual = opt.get();
        boolean yaInvalidado = actual.isInvalidado();
        actual.setInvalidado(true);
        actual.setExpiraEn(expira);
        repository.save(actual);
        return yaInvalidado;
    }

    @Scheduled(fixedRate = 60000)
    @Transactional
    public void purgarCaducados() {
        repository.deleteCaducados(Instant.now());
    }
}
