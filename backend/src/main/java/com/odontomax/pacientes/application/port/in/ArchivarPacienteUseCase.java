package com.odontomax.pacientes.application.port.in;

import java.util.UUID;

public interface ArchivarPacienteUseCase {

    /** Archiva al paciente conservando su historia; con deuda pendiente el motivo es obligatorio. */
    void archivar(UUID id, String motivo);
}
