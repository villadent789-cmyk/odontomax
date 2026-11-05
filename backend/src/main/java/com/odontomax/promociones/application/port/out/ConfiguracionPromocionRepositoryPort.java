package com.odontomax.promociones.application.port.out;

import com.odontomax.promociones.domain.model.ConfiguracionPromocion;

import java.util.Optional;

public interface ConfiguracionPromocionRepositoryPort {
    Optional<ConfiguracionPromocion> findBySedeId(String sedeId);
    ConfiguracionPromocion save(ConfiguracionPromocion config);
}
