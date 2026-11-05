package com.odontomax.identidad.infrastructure.persistence.adapter;

import com.odontomax.identidad.application.port.out.PasswordHistorialRepository;
import com.odontomax.identidad.infrastructure.persistence.entity.PasswordHistorialEntity;
import com.odontomax.identidad.infrastructure.persistence.repository.SpringDataPasswordHistorialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PasswordHistorialJpaAdapter implements PasswordHistorialRepository {

    private final SpringDataPasswordHistorialRepository passwordHistorialRepository;

    @Override
    public List<String> findUltimosTresHashes(UUID usuarioId) {
        return passwordHistorialRepository.findTop3ByUsuarioIdOrderByCreadoEnDesc(usuarioId).stream()
                .map(PasswordHistorialEntity::getPasswordHash)
                .toList();
    }

    @Override
    public void guardar(UUID usuarioId, String passwordHash) {
        passwordHistorialRepository.save(PasswordHistorialEntity.builder()
                .usuarioId(usuarioId)
                .passwordHash(passwordHash)
                .build());
    }
}
