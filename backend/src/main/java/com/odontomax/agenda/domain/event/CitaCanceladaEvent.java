package com.odontomax.agenda.domain.event;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CitaCanceladaEvent(
        UUID citaId,
        UUID pacienteId,
        String sedeId,
        String motivo,
        boolean saldoAFavorGenerado,
        BigDecimal montoSaldoAFavor,
        OffsetDateTime timestamp
) {}
