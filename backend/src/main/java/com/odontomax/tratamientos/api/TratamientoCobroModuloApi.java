package com.odontomax.tratamientos.api;

import java.math.BigDecimal;
import java.util.UUID;

/** Operaciones públicas del módulo de tratamientos que necesita el flujo de cobros. */
public interface TratamientoCobroModuloApi {

    TratamientoParaCobro validarSaldoParaCobro(UUID pacienteId, UUID tratamientoId, BigDecimal monto);

    default void verificarYCompletarSiSaldoCero(UUID tratamientoId) {}

    record TratamientoParaCobro(String nombre, UUID especialistaId, BigDecimal saldoRestante) {
        public TratamientoParaCobro(String nombre, UUID especialistaId) {
            this(nombre, especialistaId, BigDecimal.ZERO);
        }
    }
}
