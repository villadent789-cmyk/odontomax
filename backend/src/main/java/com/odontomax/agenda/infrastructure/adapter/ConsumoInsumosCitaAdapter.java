package com.odontomax.agenda.infrastructure.adapter;

import com.odontomax.agenda.application.port.out.ConsumoInsumosCitaPort;
import com.odontomax.inventario.api.ConsumoInsumosModuloApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Un fallo del inventario se registra en el log pero nunca bloquea la atención de la cita. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConsumoInsumosCitaAdapter implements ConsumoInsumosCitaPort {

    private final ConsumoInsumosModuloApi inventario;

    @Override
    public void descontarInsumos(UUID citaId, String sedeId, String procedimiento) {
        try {
            inventario.descontarPorAtencion(citaId, sedeId, procedimiento);
        } catch (RuntimeException e) {
            log.error("No se pudieron descontar los insumos de la cita {}", citaId, e);
        }
    }
}
