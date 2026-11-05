package com.odontomax.pacientes.application.port.in;

import com.odontomax.pacientes.application.dto.request.FichaIngresoDTO;

import java.util.UUID;

/** Historia clínica general del paciente: se lee y se guarda completa (una sola vez al ingresar, ampliable después). */
public interface GestionarFichaIngresoUseCase {

    /** La ficha del paciente; vacía si todavía no se llenó. */
    FichaIngresoDTO obtener(UUID pacienteId);

    FichaIngresoDTO guardar(UUID pacienteId, FichaIngresoDTO ficha);
}
