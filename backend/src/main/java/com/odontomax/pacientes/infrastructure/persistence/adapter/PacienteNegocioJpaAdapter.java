package com.odontomax.pacientes.infrastructure.persistence.adapter;

import com.odontomax.pacientes.api.PacienteResumen;
import com.odontomax.pacientes.application.dto.response.PacienteResponseDTO;
import com.odontomax.pacientes.application.port.out.PacienteNegocioPort;
import com.odontomax.pacientes.infrastructure.persistence.entity.PacienteEntity;
import com.odontomax.pacientes.infrastructure.persistence.repository.SpringDataPacienteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class PacienteNegocioJpaAdapter implements PacienteNegocioPort {

    private final SpringDataPacienteRepository repository;

    @Override
    public List<PacienteResponseDTO> listarActivos(String negocio) {
        return repository.findActivosDelNegocio(negocio).stream().map(PacienteResponseDTO::fromEntity).toList();
    }

    @Override
    public List<PacienteResponseDTO> buscar(String termino, String negocio) {
        return repository.searchByTerminoDelNegocio(termino, negocio).stream().map(PacienteResponseDTO::fromEntity).toList();
    }

    @Override
    public Page<PacienteResponseDTO> buscarPaginado(String termino, String negocio, Pageable pageable) {
        return repository.searchPaginadoDelNegocio(termino, negocio, pageable).map(PacienteResponseDTO::fromEntity);
    }

    @Override
    public List<PacienteResumen> resumenesActivos(String negocio) {
        return repository.findActivosDelNegocio(negocio).stream().map(PacienteNegocioJpaAdapter::aResumen).toList();
    }

    @Override
    public List<PacienteResumen> cumpleanerosDelDia(String negocio, int mes, int dia) {
        return repository.findCumpleanerosDelDiaDelNegocio(negocio, mes, dia).stream()
                .map(PacienteNegocioJpaAdapter::aResumen).toList();
    }

    private static PacienteResumen aResumen(PacienteEntity p) {
        return new PacienteResumen(
                p.getId(), p.getDni(), p.getNombres(), p.getApellidos(), p.getTelefono(), p.getSedeId(),
                p.getFechaNacimiento(), p.getFuenteCaptacion(), p.getCreadoEn(), p.getTotalInasistencias());
    }
}
