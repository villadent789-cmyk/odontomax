package com.odontomax.agenda.application.port.out;

import java.util.UUID;

/** Descuenta del inventario de la sede los insumos que consume el procedimiento de una cita atendida. */
public interface ConsumoInsumosCitaPort {

    void descontarInsumos(UUID citaId, String sedeId, String procedimiento);
}
