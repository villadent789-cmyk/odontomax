package com.odontomax.agenda.application.port.out;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Cuotas vencidas de los planes en cuotas de una sede (las provee el módulo de tratamientos). */
public interface CuotasVencidasPort {

    record CuotaVencida(UUID tratamientoId, UUID pacienteId, String pacienteNombre, String pacienteTelefono,
                        String tratamiento, String concepto, LocalDate venceEn, BigDecimal saldo) {}

    List<CuotaVencida> cuotasVencidasDeSede(String sedeId, LocalDate hoy);
}
