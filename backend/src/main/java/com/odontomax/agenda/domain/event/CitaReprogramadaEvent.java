package com.odontomax.agenda.domain.event;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CitaReprogramadaEvent(
        UUID citaId,
        UUID pacienteId,
        String pacienteNombre,
        String pacienteTelefono,
        String sedeId,
        LocalDate fechaAnterior,
        LocalTime horaAnterior,
        LocalDate nuevaFecha,
        LocalTime nuevoHorario,
        String motivo,
        OffsetDateTime timestamp
) {}
