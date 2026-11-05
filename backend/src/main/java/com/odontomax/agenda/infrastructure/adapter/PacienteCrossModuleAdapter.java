package com.odontomax.agenda.infrastructure.adapter;

import com.odontomax.agenda.application.port.out.PacienteReservaPort;
import com.odontomax.pacientes.api.ConsultaPacientesModuloApi;
import com.odontomax.pacientes.api.PacienteResumen;
import com.odontomax.pacientes.api.RegistroPacientesModuloApi;
import com.odontomax.pacientes.api.VerificacionPacienteModuloApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Único punto de contacto de agenda con el módulo pacientes: solo usa su API pública. */
@Component
@RequiredArgsConstructor
public class PacienteCrossModuleAdapter implements PacienteReservaPort {

    private final ConsultaPacientesModuloApi consultaPacientes;
    private final RegistroPacientesModuloApi registroPacientes;
    private final VerificacionPacienteModuloApi verificacionPaciente;

    @Override
    public boolean existePorDniEnSede(String dni, String sedeId) {
        return verificacionPaciente != null && verificacionPaciente.existePorDni(dni);
    }

    @Override
    public PacienteReservaInfo obtenerOCrearPaciente(String dni, String nombres, String apellidos, String telefono, String sedeId) {
        return toInfo(registroPacientes.obtenerOCrearPorDni(dni, nombres, apellidos, telefono, sedeId));
    }

    @Override
    public java.util.Map<UUID, Integer> inasistenciasDe(java.util.Collection<UUID> pacienteIds) {
        java.util.Map<UUID, Integer> conteos = new java.util.HashMap<>();
        consultaPacientes.findAllById(pacienteIds).forEach((id, p) -> conteos.put(id, p.totalInasistencias()));
        return conteos;
    }

    @Override
    public Optional<PacienteReservaInfo> findById(UUID id) {
        return consultaPacientes.findById(id).map(PacienteCrossModuleAdapter::toInfo);
    }

    @Override
    public List<PacienteCumpleaneroInfo> obtenerCumpleanerosDelDia(String sedeId, int mes, int dia) {
        return consultaPacientes.findCumpleanerosDelDia(sedeId, mes, dia).stream()
                .map(p -> new PacienteCumpleaneroInfo(
                        p.id(),
                        p.dni(),
                        nombreCompleto(p),
                        p.telefono(),
                        p.totalInasistencias(),
                        p.totalInasistencias() >= 2
                ))
                .toList();
    }

    private static PacienteReservaInfo toInfo(PacienteResumen p) {
        return new PacienteReservaInfo(p.id(), p.dni(), nombreCompleto(p), p.telefono());
    }

    /** Misma fórmula que usaba el modelo de dominio: nombres + " " + apellidos, con nulos como vacío. */
    private static String nombreCompleto(PacienteResumen p) {
        return (p.nombres() != null ? p.nombres() : "") + " " + (p.apellidos() != null ? p.apellidos() : "");
    }
}
