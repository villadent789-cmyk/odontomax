package com.odontomax.agenda.application.port.out;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Puerto para procesar y registrar adelantos de citas conforme a la Sección 4 de especificaciones.
 * Diseñado para admitir registro manual (Yape y Efectivo) y desacoplar futuras pasarelas (Culqi, Izipay, Niubiz).
 */
public interface PagoAdelantoPort {

    void registrarAdelanto(UUID citaId, UUID pacienteId, UUID tratamientoId, String sedeId,
                           BigDecimal monto, String medioPago, String numeroOperacion, String usuario);
}
