package com.odontomax.promociones.domain.event;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PromocionAplicadaEvent(
        UUID promocionId,
        UUID pacienteId,
        UUID citaId,
        String beneficio,
        String usuario,
        OffsetDateTime aplicadoEn
) {
}
