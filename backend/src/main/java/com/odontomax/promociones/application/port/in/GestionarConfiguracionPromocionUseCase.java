package com.odontomax.promociones.application.port.in;

import com.odontomax.promociones.application.dto.ConfiguracionPromocionDTO;
import com.odontomax.promociones.application.dto.ConfiguracionPromocionRequestDTO;

public interface GestionarConfiguracionPromocionUseCase {
    ConfiguracionPromocionDTO obtenerConfiguracion(String sedeId);
    ConfiguracionPromocionDTO guardarConfiguracion(ConfiguracionPromocionRequestDTO request);
}
