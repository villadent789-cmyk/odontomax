package com.odontomax.agenda.domain.event;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AdelantoRegistradoEvent(
        UUID citaId,
        UUID pacienteId,
        String sedeId,
        BigDecimal monto,
        String medioPago,
        String numeroOperacion,
        String usuario,
        OffsetDateTime timestamp
) {}
