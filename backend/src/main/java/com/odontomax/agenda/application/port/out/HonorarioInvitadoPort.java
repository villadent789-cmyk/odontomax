package com.odontomax.agenda.application.port.out;

import java.math.BigDecimal;
import java.util.UUID;

/** Pago de honorarios a un doctor invitado al terminar su atención: sale como egreso de la caja de la sede. */
public interface HonorarioInvitadoPort {

    /**
     * Registra el egreso. Exige la caja de la sede abierta y respeta el tope de egresos en efectivo de recepción.
     *
     * @throws org.springframework.web.server.ResponseStatusException 409 si la caja está cerrada, 403 si supera el tope
     */
    void pagar(UUID citaId, String sedeId, String doctorNombre, String pacienteNombre, BigDecimal monto, String metodoPago);
}
