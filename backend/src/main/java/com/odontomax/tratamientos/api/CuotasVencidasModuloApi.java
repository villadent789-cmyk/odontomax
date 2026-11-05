package com.odontomax.tratamientos.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Cuotas de planes en cuotas que ya vencieron sin pagarse, para que recepción las cobre. */
public interface CuotasVencidasModuloApi {

    record CuotaVencida(UUID tratamientoId, UUID pacienteId, String pacienteNombre, String pacienteTelefono,
                        String tratamiento, String concepto, LocalDate venceEn, BigDecimal saldo) {}

    List<CuotaVencida> cuotasVencidasDeSede(String sedeId, LocalDate hoy);
}
