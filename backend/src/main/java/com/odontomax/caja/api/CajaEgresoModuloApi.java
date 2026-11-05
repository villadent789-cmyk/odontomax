package com.odontomax.caja.api;

import java.math.BigDecimal;
import java.util.UUID;

public interface CajaEgresoModuloApi {
    UUID registrarEgresoDeSistema(
            String sedeId,
            BigDecimal monto,
            String metodoPago,
            String concepto,
            String referenciaTipo,
            UUID referenciaId
    );

    UUID revertirEgresoDeSistema(
            String sedeId,
            BigDecimal monto,
            String metodoPago,
            String concepto,
            String referenciaTipo,
            UUID referenciaId
    );
}
