package com.odontomax.agenda.domain.event;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CitaConfirmadaEvent(
        UUID citaId,
        UUID pacienteId,
        String pacienteNombre,
        String pacienteTelefono,
        String sedeId,
        LocalDate fecha,
        LocalTime hora,
        boolean conAdelanto,
        BigDecimal montoAdelanto,
        OffsetDateTime timestamp
) {}
