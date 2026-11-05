package com.odontomax.laboratorio.service;

import com.odontomax.infrastructure.persistence.repository.SpringDataOrdenLaboratorioRepository;
import com.odontomax.laboratorio.api.ConsultaLaboratorioModuloApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConsultaLaboratorioModuloService implements ConsultaLaboratorioModuloApi {

    private final SpringDataOrdenLaboratorioRepository ordenRepository;

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, BigDecimal> montoLaboratorioPorDoctor(Collection<String> sedes, LocalDate desde, LocalDate hasta) {
        Map<UUID, BigDecimal> resultado = new HashMap<>();
        if (sedes == null || sedes.isEmpty()) {
            return resultado;
        }
        for (Object[] fila : ordenRepository.montoPorDoctor(sedes, desde, hasta)) {
            resultado.put((UUID) fila[0], (BigDecimal) fila[1]);
        }
        return resultado;
    }
}
