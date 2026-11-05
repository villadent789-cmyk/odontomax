package com.odontomax.promociones.domain.event;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PromocionEnviadaEvent(
        UUID promocionId,
        UUID pacienteId,
        String usuario,
        OffsetDateTime enviadoEn
) {
}
