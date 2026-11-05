package com.odontomax.pacientes.infrastructure.persistence.adapter;

import com.odontomax.pacientes.application.port.out.SolicitudCorreccionRepositoryPort;
import com.odontomax.pacientes.domain.model.SolicitudCorreccion;
import com.odontomax.pacientes.infrastructure.persistence.entity.SolicitudCorreccionEntity;
import com.odontomax.pacientes.infrastructure.persistence.repository.SpringDataSolicitudCorreccionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SolicitudCorreccionJpaAdapter implements SolicitudCorreccionRepositoryPort {

    private final SpringDataSolicitudCorreccionRepository repository;

    @Override
    public SolicitudCorreccion save(SolicitudCorreccion s) {
        return toDomain(repository.save(toEntity(s)));
    }

    @Override
    public Optional<SolicitudCorreccion> findById(UUID id) {
        return repository.findById(id).map(SolicitudCorreccionJpaAdapter::toDomain);
    }

    @Override
    public List<SolicitudCorreccion> findByEstadoOrderBySolicitadoEnDesc(String estado) {
        return repository.findByEstadoOrderBySolicitadoEnDesc(estado).stream().map(SolicitudCorreccionJpaAdapter::toDomain).toList();
    }

    @Override
    public List<SolicitudCorreccion> findByPacienteIdOrderBySolicitadoEnDesc(UUID pacienteId) {
        return repository.findByPacienteIdOrderBySolicitadoEnDesc(pacienteId).stream().map(SolicitudCorreccionJpaAdapter::toDomain).toList();
    }

    private static SolicitudCorreccion toDomain(SolicitudCorreccionEntity e) {
        return SolicitudCorreccion.builder()
                .id(e.getId()).pacienteId(e.getPacienteId()).sedeId(e.getSedeId())
                .nombresAnterior(e.getNombresAnterior()).apellidosAnterior(e.getApellidosAnterior())
                .dniAnterior(e.getDniAnterior()).fechaNacimientoAnterior(e.getFechaNacimientoAnterior())
                .nombresNuevo(e.getNombresNuevo()).apellidosNuevo(e.getApellidosNuevo())
                .dniNuevo(e.getDniNuevo()).fechaNacimientoNueva(e.getFechaNacimientoNueva())
                .motivo(e.getMotivo()).estado(e.getEstado())
                .solicitadoPor(e.getSolicitadoPor()).solicitadoPorNombre(e.getSolicitadoPorNombre()).solicitadoEn(e.getSolicitadoEn())
                .resueltoPor(e.getResueltoPor()).resueltoPorNombre(e.getResueltoPorNombre()).resueltoEn(e.getResueltoEn())
                .comentarioResolucion(e.getComentarioResolucion())
                .build();
    }

    private static SolicitudCorreccionEntity toEntity(SolicitudCorreccion s) {
        SolicitudCorreccionEntity e = SolicitudCorreccionEntity.builder()
                .id(s.getId()).pacienteId(s.getPacienteId()).sedeId(s.getSedeId())
                .nombresAnterior(s.getNombresAnterior()).apellidosAnterior(s.getApellidosAnterior())
                .dniAnterior(s.getDniAnterior()).fechaNacimientoAnterior(s.getFechaNacimientoAnterior())
                .nombresNuevo(s.getNombresNuevo()).apellidosNuevo(s.getApellidosNuevo())
                .dniNuevo(s.getDniNuevo()).fechaNacimientoNueva(s.getFechaNacimientoNueva())
                .motivo(s.getMotivo()).estado(s.getEstado())
                .solicitadoPor(s.getSolicitadoPor()).solicitadoPorNombre(s.getSolicitadoPorNombre())
                .resueltoPor(s.getResueltoPor()).resueltoPorNombre(s.getResueltoPorNombre()).resueltoEn(s.getResueltoEn())
                .comentarioResolucion(s.getComentarioResolucion())
                .build();
        if (s.getSolicitadoEn() != null) {
            e.setSolicitadoEn(s.getSolicitadoEn());
        }
        return e;
    }
}
