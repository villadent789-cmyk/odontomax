package com.odontomax.agenda.application.port.out;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface PacienteReservaPort {

    record PacienteReservaInfo(UUID id, String dni, String nombreCompleto, String telefono) {}

    record PacienteCumpleaneroInfo(UUID id, String dni, String nombreCompleto, String telefono,
                                  int totalInasistencias, boolean avisoInasistencias) {}

    PacienteReservaInfo obtenerOCrearPaciente(String dni, String nombres, String apellidos, String telefono, String sedeId);

    boolean existePorDniEnSede(String dni, String sedeId);

    Optional<PacienteReservaInfo> findById(UUID id);

    /** Inasistencias acumuladas de cada paciente, para marcar a quien falta seguido. */
    Map<UUID, Integer> inasistenciasDe(Collection<UUID> pacienteIds);

    List<PacienteCumpleaneroInfo> obtenerCumpleanerosDelDia(String sedeId, int mes, int dia);
}
