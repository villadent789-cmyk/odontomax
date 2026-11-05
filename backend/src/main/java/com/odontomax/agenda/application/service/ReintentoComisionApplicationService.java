package com.odontomax.agenda.application.service;

import com.odontomax.agenda.application.port.in.ReintentarComisionUseCase;
import com.odontomax.agenda.application.port.out.CitaRepositoryPort;
import com.odontomax.agenda.application.port.out.ComisionCitaPort;
import com.odontomax.identidad.api.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReintentoComisionApplicationService implements ReintentarComisionUseCase {

    private final CitaRepositoryPort citaRepository;
    private final ComisionCitaPort comisionCitaPort;

    @Override
    @Transactional
    public void reintentar(UUID citaId) {
        var cita = citaRepository.findById(citaId)
                .orElseThrow(() -> new IllegalArgumentException("Cita no encontrada con ID: " + citaId));
        SecurityUtils.validarAccesoSede(cita.getSedeId());
        comisionCitaPort.generarComisionSiAplica(citaId);
    }
}
