package com.odontomax.agenda.infrastructure.persistence.adapter;

import com.odontomax.agenda.application.port.out.CitaContactoPort;
import com.odontomax.agenda.infrastructure.persistence.entity.CitaContactoEntity;
import com.odontomax.agenda.infrastructure.persistence.repository.SpringDataCitaContactoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CitaContactoJpaAdapter implements CitaContactoPort {

    private final SpringDataCitaContactoRepository repository;

    @Override
    public void registrar(UUID citaId, String medio, String resultado, String notas, String usuario) {
        repository.save(CitaContactoEntity.builder()
                .citaId(citaId)
                .medio(medio)
                .resultado(resultado)
                .notas(notas)
                .usuario(usuario)
                .build());
    }
}
