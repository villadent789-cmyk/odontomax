package com.odontomax.application.port.out;

import com.odontomax.application.dto.response.CajaResumenDiarioDTO;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public interface CajaPeriodoPort {

    CajaTotalesSesion calcularTotales(UUID sesionId);

    void guardarCierreMetodos(UUID sesionId, BigDecimal montoEsperadoYape, BigDecimal montoContadoYape,
                              BigDecimal montoEsperadoPlin, BigDecimal montoContadoPlin,
                              BigDecimal montoEsperadoTarjeta, BigDecimal montoContadoTarjeta);

    CajaResumenDiarioDTO obtenerResumenDiario(String sedeId, Instant desde, Instant hasta);
}
