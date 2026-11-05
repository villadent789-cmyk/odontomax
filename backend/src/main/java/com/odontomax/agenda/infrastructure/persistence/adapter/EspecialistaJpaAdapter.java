package com.odontomax.agenda.infrastructure.persistence.adapter;

import com.odontomax.agenda.api.ConsultaEspecialistasModuloApi;
import com.odontomax.agenda.application.port.out.EspecialistaHorarioRepositoryPort;
import com.odontomax.agenda.application.port.out.EspecialistaRepositoryPort;
import com.odontomax.agenda.application.port.out.VerificacionHistorialClinicoPort;
import com.odontomax.agenda.domain.model.Especialista;
import com.odontomax.agenda.domain.model.HorarioEspecialista;
import com.odontomax.agenda.infrastructure.persistence.entity.EspecialistaEntity;
import com.odontomax.agenda.infrastructure.persistence.entity.EspecialistaHorarioEntity;
import com.odontomax.agenda.infrastructure.persistence.mapper.EspecialistaMapper;
import com.odontomax.agenda.infrastructure.persistence.mapper.HorarioEspecialistaMapper;
import com.odontomax.agenda.infrastructure.persistence.repository.SpringDataEspecialistaHorarioRepository;
import com.odontomax.agenda.infrastructure.persistence.repository.SpringDataEspecialistaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class EspecialistaJpaAdapter implements
        EspecialistaRepositoryPort,
        EspecialistaHorarioRepositoryPort,
        VerificacionHistorialClinicoPort,
        ConsultaEspecialistasModuloApi {

    private final SpringDataEspecialistaRepository especialistaRepository;
    private final SpringDataEspecialistaHorarioRepository horarioRepository;
    private final EspecialistaMapper especialistaMapper;
    private final HorarioEspecialistaMapper horarioMapper;

    @Override
    public Optional<Especialista> findById(UUID id) {
        return especialistaRepository.findById(id).map(this::enrichWithSedes);
    }

    @Override
    public Optional<Especialista> findByUsuarioId(UUID usuarioId) {
        return especialistaRepository.findByUsuarioId(usuarioId).map(this::enrichWithSedes);
    }

    @Override
    public Optional<Especialista> findFirstByEmailIgnoreCaseAndUsuarioIdIsNull(String email) {
        return especialistaRepository.findFirstByEmailIgnoreCaseAndUsuarioIdIsNull(email).map(this::enrichWithSedes);
    }

    @Override
    public List<Especialista> findAllByOrderByNombreAsc() {
        return especialistaRepository.findAllByOrderByNombreAsc().stream()
                .map(this::enrichWithSedes)
                .toList();
    }

    @Override
    public List<Especialista> findByActivoTrueOrderByNombreAsc() {
        return especialistaRepository.findByActivoTrueOrderByNombreAsc().stream()
                .map(this::enrichWithSedes)
                .toList();
    }

    @Override
    public List<Especialista> findBySedeIdAndActivoTrue(String sedeId) {
        return especialistaRepository.findBySedeIdAndActivoTrue(sedeId).stream()
                .map(this::enrichWithSedes)
                .toList();
    }

    @Override
    public boolean existsByCop(String cop) {
        return especialistaRepository.existsByCop(cop);
    }

    @Override
    public boolean existsByCopAndIdNot(String cop, UUID id) {
        return especialistaRepository.existsByCopAndIdNot(cop, id);
    }

    @Override
    public boolean existsById(UUID id) {
        return especialistaRepository.existsById(id);
    }

    @Override
    public Especialista save(Especialista domain) {
        EspecialistaEntity entity = especialistaMapper.toEntity(domain);
        EspecialistaEntity saved = especialistaRepository.saveAndFlush(entity);
        return enrichWithSedes(saved);
    }

    @Override
    public void delete(UUID id) {
        especialistaRepository.deleteById(id);
        especialistaRepository.flush();
    }

    @Override
    public List<String> findSedeIdsByEspecialistaId(UUID especialistaId) {
        return especialistaRepository.findSedeIdsByEspecialistaId(especialistaId);
    }

    @Override
    public void vincularSede(UUID especialistaId, String sedeId) {
        especialistaRepository.vincularSede(especialistaId, sedeId);
    }

    @Override
    public void desvincularSede(UUID especialistaId, String sedeId) {
        especialistaRepository.desvincularSede(especialistaId, sedeId);
    }

    @Override
    public void desvincularTodasLasSedes(UUID especialistaId) {
        especialistaRepository.desvincularTodasLasSedes(especialistaId);
    }

    @Override
    public Map<String, Integer> findSillonesPorSede(UUID especialistaId) {
        Map<String, Integer> sillones = new TreeMap<>();
        especialistaRepository.findSillonesPorSede(especialistaId).forEach(row -> {
            if (row[1] != null) {
                sillones.put((String) row[0], ((Number) row[1]).intValue());
            }
        });
        return sillones;
    }

    @Override
    public Optional<UUID> findEspecialistaIdBySedeYSillon(String sedeId, int sillonNumero) {
        return especialistaRepository.findEspecialistaIdBySedeYSillon(sedeId, sillonNumero);
    }

    @Override
    public void actualizarSillon(UUID especialistaId, String sedeId, Integer sillonNumero) {
        especialistaRepository.actualizarSillon(especialistaId, sedeId, sillonNumero);
    }

    @Override
    public List<HorarioEspecialista> findByEspecialistaId(UUID especialistaId) {
        return horarioRepository.findByEspecialistaIdOrderBySedeIdAscDiaSemanaAscHoraInicioAsc(especialistaId).stream()
                .map(horarioMapper::toDomain)
                .toList();
    }

    @Override
    public List<HorarioEspecialista> findByEspecialistaIdAndDiaSemanaAndActivoTrue(UUID especialistaId, int diaSemana) {
        return horarioRepository.findByEspecialistaIdAndDiaSemanaAndActivoTrue(especialistaId, diaSemana).stream()
                .map(horarioMapper::toDomain)
                .toList();
    }

    @Override
    public List<HorarioEspecialista> findByEspecialistaIdAndSedeIdAndDiaSemanaAndActivoTrue(UUID especialistaId, String sedeId, int diaSemana) {
        return horarioRepository.findByEspecialistaIdAndSedeIdAndDiaSemanaAndActivoTrue(especialistaId, sedeId.trim(), diaSemana).stream()
                .map(horarioMapper::toDomain)
                .toList();
    }

    @Override
    public void saveAll(List<HorarioEspecialista> horarios) {
        List<EspecialistaHorarioEntity> entities = horarios.stream()
                .map(horarioMapper::toEntity)
                .toList();
        horarioRepository.saveAll(entities);
        horarioRepository.flush();
    }

    @Override
    public void eliminarPorEspecialista(UUID especialistaId) {
        horarioRepository.eliminarPorEspecialista(especialistaId);
    }

    @Override
    public boolean tieneHistorialClinico(UUID especialistaId) {
        return especialistaRepository.tieneHistorialClinico(especialistaId);
    }

    @Override
    public Optional<UUID> findEspecialistaIdByUsuarioId(UUID usuarioId) {
        return especialistaRepository.findByUsuarioId(usuarioId).map(EspecialistaEntity::getId);
    }

    @Override
    public Optional<String> findNombreById(UUID especialistaId) {
        return especialistaRepository.findById(especialistaId).map(EspecialistaEntity::getNombre);
    }

    @Override
    public Optional<MetaDoctor> obtenerMeta(UUID especialistaId) {
        return especialistaRepository.findById(especialistaId)
                .map(e -> new MetaDoctor(e.getMetaMensual(), e.getComisionExcedentePorcentaje()));
    }

    @Override
    public Set<UUID> idsActivosEnSede(String sedeId) {
        return findBySedeIdAndActivoTrue(sedeId).stream().map(Especialista::getId).collect(java.util.stream.Collectors.toSet());
    }

    @Override
    public boolean odontologoAtiendeAlPaciente(UUID usuarioId, UUID pacienteId) {
        return especialistaRepository.existeCitaDelUsuarioConElPaciente(usuarioId, pacienteId);
    }

    @Override
    public Set<UUID> pacientesDelOdontologo(UUID usuarioId) {
        return especialistaRepository.findPacienteIdsByUsuarioId(usuarioId);
    }

    private Especialista enrichWithSedes(EspecialistaEntity entity) {
        Especialista domain = especialistaMapper.toDomain(entity);
        if (domain != null) {
            domain.setSedes(especialistaRepository.findSedeIdsByEspecialistaId(entity.getId()));
            domain.setSillonesPorSede(findSillonesPorSede(entity.getId()));
        }
        return domain;
    }
}
