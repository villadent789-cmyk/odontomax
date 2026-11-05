package com.odontomax.infrastructure.persistence.adapter;

import com.odontomax.domain.model.Radiografia;
import com.odontomax.domain.port.out.RadiografiaRepositoryPort;
import com.odontomax.infrastructure.persistence.entity.RadiografiaEntity;
import com.odontomax.infrastructure.persistence.repository.SpringDataRadiografiaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class RadiografiaRepositoryAdapter implements RadiografiaRepositoryPort {

    private final SpringDataRadiografiaRepository repository;

    @Override
    public Radiografia save(Radiografia radiografia) {
        RadiografiaEntity entity = toEntity(radiografia);
        RadiografiaEntity saved = repository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<Radiografia> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public List<Radiografia> findByPacienteId(UUID pacienteId) {
        return repository.findByPacienteIdOrderByFechaTomaDesc(pacienteId)
                .stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<Radiografia> findByPacienteIdAndTipo(UUID pacienteId, String tipoEstudio) {
        return repository.findByPacienteIdAndTipoEstudioOrderByFechaTomaDesc(pacienteId, tipoEstudio)
                .stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public void deleteById(UUID id) {
        repository.deleteById(id);
    }

    private Radiografia toDomain(RadiografiaEntity e) {
        return Radiografia.builder()
                .id(e.getId())
                .pacienteId(e.getPacienteId())
                .doctorId(e.getDoctorId())
                .tipoEstudio(e.getTipoEstudio())
                .piezaFdi(e.getPiezaFdi())
                .etapa(e.getEtapa())
                .archivoUrl(e.getArchivoUrl())
                .nombreArchivo(e.getNombreArchivo())
                .tamanoBytes(e.getTamanoBytes() != null ? e.getTamanoBytes() : 0L)
                .diagnosticoRadiografico(e.getDiagnosticoRadiografico())
                .fechaToma(e.getFechaToma())
                .build();
    }

    private RadiografiaEntity toEntity(Radiografia d) {
        return RadiografiaEntity.builder()
                .id(d.getId())
                .pacienteId(d.getPacienteId())
                .doctorId(d.getDoctorId())
                .tipoEstudio(d.getTipoEstudio())
                .piezaFdi(d.getPiezaFdi())
                .etapa(d.getEtapa())
                .archivoUrl(d.getArchivoUrl())
                .nombreArchivo(d.getNombreArchivo())
                .tamanoBytes(d.getTamanoBytes())
                .diagnosticoRadiografico(d.getDiagnosticoRadiografico())
                .fechaToma(d.getFechaToma())
                .build();
    }
}
