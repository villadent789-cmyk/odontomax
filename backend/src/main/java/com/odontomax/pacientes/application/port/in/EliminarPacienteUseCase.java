package com.odontomax.pacientes.application.port.in;

import com.odontomax.identidad.api.ClinicaUserPrincipal;

import java.util.UUID;

public interface EliminarPacienteUseCase {

    /**
     * Eliminación física del paciente y de sus dependientes sin protección legal (citas, odontograma, tratamientos,
     * radiografías y sus archivos). Con registros protegidos no se borra nada. El motivo queda en la auditoría.
     */
    void eliminar(UUID id, ClinicaUserPrincipal principal, String clientIp, String motivo);
}
