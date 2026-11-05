package com.odontomax.inventario.api;

import java.util.UUID;

/** Interfaz pública del módulo de inventario para descontar los insumos que consume una atención. */
public interface ConsumoInsumosModuloApi {

    /**
     * Descuenta del stock de la sede los insumos de la receta del servicio que se atendió (por id o nombre exacto).
     * Es idempotente por cita e insumo y nunca falla por falta de stock: descuenta lo que haya y deja una alerta con
     * el faltante. No hace nada si el servicio no es de Villadent o no tiene receta.
     */
    void descontarPorAtencion(UUID citaId, String sedeId, String servicioONombre);
}
