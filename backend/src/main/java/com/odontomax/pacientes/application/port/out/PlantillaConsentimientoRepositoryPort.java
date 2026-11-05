package com.odontomax.pacientes.application.port.out;

import com.odontomax.pacientes.domain.model.PlantillaConsentimiento;

import java.util.List;

public interface PlantillaConsentimientoRepositoryPort {
    List<PlantillaConsentimiento> findByActivoTrueOrderByTituloAsc();
}