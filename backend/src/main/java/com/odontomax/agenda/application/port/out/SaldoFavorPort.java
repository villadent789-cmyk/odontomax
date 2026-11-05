package com.odontomax.agenda.application.port.out;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Puerto para registrar saldos a favor de pacientes por cancelaciones válidas (>= 24 horas)
 * conforme a la política de cancelación de la Sección 4 de especificaciones.
 */
public interface SaldoFavorPort {

    void acreditarSaldoFavor(UUID pacienteId, UUID citaId, BigDecimal monto, String motivo);
}
