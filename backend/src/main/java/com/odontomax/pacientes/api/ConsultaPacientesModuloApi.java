package com.odontomax.pacientes.api;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Consulta de pacientes para otros módulos, sin exponer la persistencia de pacientes. */
public interface ConsultaPacientesModuloApi {

    Optional<PacienteResumen> findById(UUID id);

    /** Pacientes existentes entre los ids indicados, por id; los ids desconocidos no aparecen. */
    Map<UUID, PacienteResumen> findAllById(Collection<UUID> ids);

    /** Pacientes no archivados; con sedeId null, de todas las sedes; con cualquier otro valor, solo los de esa sede exacta. */
    List<PacienteResumen> listarActivos(String sedeId);

    /**
     * Pacientes no archivados que cumplen años ese mes y día y aceptaron promociones.
     * sedeId null, en blanco o "ALL" significa todas las sedes.
     */
    List<PacienteResumen> findCumpleanerosDelDia(String sedeId, int mes, int dia);
}
