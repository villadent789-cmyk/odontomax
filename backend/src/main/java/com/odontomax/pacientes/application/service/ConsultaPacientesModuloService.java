package com.odontomax.pacientes.application.service;

import com.odontomax.identidad.api.SecurityUtils;
import com.odontomax.pacientes.api.ConsultaPacientesModuloApi;
import com.odontomax.pacientes.api.PacienteResumen;
import com.odontomax.pacientes.application.port.out.PacienteNegocioPort;
import com.odontomax.pacientes.infrastructure.persistence.entity.PacienteEntity;
import com.odontomax.pacientes.infrastructure.persistence.repository.SpringDataPacienteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConsultaPacientesModuloService implements ConsultaPacientesModuloApi {

    private final SpringDataPacienteRepository pacienteRepository;
    private PacienteNegocioPort pacienteNegocio;

    /** Inyección por setter para no cambiar el constructor de este servicio. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setPacienteNegocio(PacienteNegocioPort pacienteNegocio) {
        this.pacienteNegocio = pacienteNegocio;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PacienteResumen> findById(UUID id) {
        return pacienteRepository.findById(id).map(ConsultaPacientesModuloService::toResumen);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, PacienteResumen> findAllById(Collection<UUID> ids) {
        Map<UUID, PacienteResumen> resultado = new LinkedHashMap<>();
        for (PacienteEntity p : pacienteRepository.findAllById(ids)) {
            resultado.putIfAbsent(p.getId(), toResumen(p));
        }
        return resultado;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PacienteResumen> listarActivos(String sedeId) {
        String negocio = SecurityUtils.negocioActivo();
        if (sedeId == null && negocio != null) {
            return pacienteNegocio.resumenesActivos(negocio);
        }
        List<PacienteEntity> pacientes = (sedeId != null)
                ? pacienteRepository.findActivosBySede(sedeId)
                : pacienteRepository.findAllActivos(null);
        return pacientes.stream().map(ConsultaPacientesModuloService::toResumen).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PacienteResumen> findCumpleanerosDelDia(String sedeId, int mes, int dia) {
        String negocio = SecurityUtils.negocioActivo();
        boolean todas = sedeId == null || sedeId.isBlank() || "ALL".equalsIgnoreCase(sedeId);
        if (todas && negocio != null) {
            return pacienteNegocio.cumpleanerosDelDia(negocio, mes, dia);
        }
        return pacienteRepository.findCumpleanerosDelDia(sedeId, mes, dia).stream()
                .map(ConsultaPacientesModuloService::toResumen)
                .toList();
    }

    static PacienteResumen toResumen(PacienteEntity p) {
        return new PacienteResumen(
                p.getId(), p.getDni(), p.getNombres(), p.getApellidos(), p.getTelefono(), p.getSedeId(),
                p.getFechaNacimiento(), p.getFuenteCaptacion(), p.getCreadoEn(), p.getTotalInasistencias());
    }
}
