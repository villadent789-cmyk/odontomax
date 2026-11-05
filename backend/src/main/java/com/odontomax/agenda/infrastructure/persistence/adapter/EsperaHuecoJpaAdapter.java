package com.odontomax.agenda.infrastructure.persistence.adapter;

import com.odontomax.agenda.application.port.out.EsperaHuecoRepositoryPort;
import com.odontomax.agenda.domain.model.EsperaHueco;
import com.odontomax.agenda.infrastructure.persistence.entity.EsperaHuecoEntity;
import com.odontomax.agenda.infrastructure.persistence.repository.SpringDataEsperaHuecoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class EsperaHuecoJpaAdapter implements EsperaHuecoRepositoryPort {

    private static final List<String> VIGENTES = List.of(EsperaHueco.ACTIVA, EsperaHueco.OFRECIDA);

    private final SpringDataEsperaHuecoRepository repository;

    @Override
    public EsperaHueco save(EsperaHueco e) {
        EsperaHuecoEntity entity = EsperaHuecoEntity.builder()
                .id(e.getId() != null ? e.getId() : UUID.randomUUID())
                .sedeId(e.getSedeId())
                .pacienteId(e.getPacienteId())
                .pacienteNombre(e.getPacienteNombre())
                .pacienteTelefono(e.getPacienteTelefono())
                .especialistaId(e.getEspecialistaId())
                .motivo(e.getMotivo())
                .notas(e.getNotas())
                .estado(e.getEstado())
                .creadoPor(e.getCreadoPor())
                .creadoEn(e.getCreadoEn() != null ? e.getCreadoEn() : Instant.now())
                .actualizadoEn(Instant.now())
                .build();
        return aDominio(repository.save(entity));
    }

    @Override
    public Optional<EsperaHueco> findById(UUID id) {
        return repository.findById(id).map(EsperaHuecoJpaAdapter::aDominio);
    }

    @Override
    public List<EsperaHueco> findVigentesBySede(String sedeId) {
        return repository.findBySedeIdAndEstadoInOrderByCreadoEnAsc(sedeId, VIGENTES).stream()
                .map(EsperaHuecoJpaAdapter::aDominio).toList();
    }

    @Override
    public boolean existeVigenteDelPaciente(String sedeId, UUID pacienteId) {
        return repository.existsBySedeIdAndPacienteIdAndEstadoIn(sedeId, pacienteId, VIGENTES);
    }

    private static EsperaHueco aDominio(EsperaHuecoEntity e) {
        return new EsperaHueco(e.getId(), e.getSedeId(), e.getPacienteId(), e.getPacienteNombre(), e.getPacienteTelefono(),
                e.getEspecialistaId(), e.getMotivo(), e.getNotas(), e.getEstado(), e.getCreadoPor(), e.getCreadoEn());
    }
}
