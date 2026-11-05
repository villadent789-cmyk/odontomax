package com.odontomax.laboratorio.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Consulta pública del módulo de laboratorio para quien necesita saber cuánto de lo cobrado es de laboratorio. */
public interface ConsultaLaboratorioModuloApi {

    /**
     * Monto de trabajos de laboratorio (órdenes no canceladas, solicitadas entre las fechas, ambas incluidas) por
     * doctor, en las sedes indicadas. Es el precio al paciente; si falta, el costo del laboratorio.
     */
    Map<UUID, BigDecimal> montoLaboratorioPorDoctor(Collection<String> sedes, LocalDate desde, LocalDate hasta);
}
