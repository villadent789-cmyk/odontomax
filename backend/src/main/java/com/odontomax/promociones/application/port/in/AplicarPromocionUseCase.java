package com.odontomax.promociones.application.port.in;

import com.odontomax.promociones.application.dto.AplicarPromocionRequestDTO;
import com.odontomax.promociones.application.dto.PromocionCumpleanosDTO;

import java.util.UUID;

public interface AplicarPromocionUseCase {
    PromocionCumpleanosDTO aplicarPromocion(UUID promocionId, AplicarPromocionRequestDTO request, String usuario);
}
