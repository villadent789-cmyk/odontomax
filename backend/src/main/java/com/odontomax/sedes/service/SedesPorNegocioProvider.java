package com.odontomax.sedes.service;

import com.odontomax.identidad.api.SecurityUtils;
import com.odontomax.sedes.infrastructure.persistence.entity.SedeEntity;
import com.odontomax.sedes.infrastructure.persistence.repository.SpringDataSedeRepository;
import com.odontomax.sedes.model.UnidadDeNegocio;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Dice a la seguridad qué sedes componen cada negocio, para acotar el "todas las sedes" del administrador general. */
@Component
@RequiredArgsConstructor
public class SedesPorNegocioProvider implements SecurityUtils.NegocioSedesProvider {

    private final SpringDataSedeRepository sedeRepository;

    @PostConstruct
    void registrar() {
        SecurityUtils.registrarProveedorNegocio(this);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> sedesDelNegocio(String negocio) {
        return sedeRepository.findByUnidadDeNegocio(UnidadDeNegocio.valueOf(negocio)).stream()
                .map(SedeEntity::getId)
                .toList();
    }
}
