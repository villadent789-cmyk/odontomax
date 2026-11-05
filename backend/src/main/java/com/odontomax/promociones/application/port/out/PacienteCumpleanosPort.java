package com.odontomax.promociones.application.port.out;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface PacienteCumpleanosPort {

    record DatosPacienteCumpleanosDTO(
            UUID id,
            String nombreCompleto,
            String telefono,
            String dni,
            LocalDate fechaNacimiento,
            boolean consentimientoPromociones,
            String sedeRegistroId
    ) {}

    Optional<DatosPacienteCumpleanosDTO> findPaciente(UUID pacienteId);
}
