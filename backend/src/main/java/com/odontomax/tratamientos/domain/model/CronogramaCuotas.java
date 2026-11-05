package com.odontomax.tratamientos.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Cronograma de cuotas de un plan (p. ej. ortodoncia: cuota inicial y cuotas mensuales).
 * Lo pagado no se guarda por cuota: se reparte aquí, en orden, entre las cuotas más antiguas, así un cobro
 * anulado o una regularización histórica no deja el cronograma desincronizado.
 */
public final class CronogramaCuotas {

    public static final int MAX_CUOTAS_MENSUALES = 60;

    public enum Estado { PAGADA, PARCIAL, PENDIENTE, VENCIDA }

    public record Cuota(int numero, String concepto, LocalDate venceEn, BigDecimal monto) {}

    public record CuotaConEstado(Cuota cuota, BigDecimal pagado, BigDecimal saldo, Estado estado) {}

    private CronogramaCuotas() {}

    /** Cuota 0 = inicial (vence el primer día) y luego una cada mes. */
    public static List<Cuota> generar(BigDecimal montoInicial, BigDecimal montoMensual, int cantidadMensuales, LocalDate primerVencimiento) {
        BigDecimal inicial = montoInicial != null ? montoInicial : BigDecimal.ZERO;
        if (inicial.signum() < 0) {
            throw new IllegalArgumentException("La cuota inicial no puede ser negativa.");
        }
        if (cantidadMensuales < 0 || cantidadMensuales > MAX_CUOTAS_MENSUALES) {
            throw new IllegalArgumentException("La cantidad de cuotas mensuales debe estar entre 0 y " + MAX_CUOTAS_MENSUALES + ".");
        }
        if (cantidadMensuales > 0 && (montoMensual == null || montoMensual.signum() <= 0)) {
            throw new IllegalArgumentException("La cuota mensual debe ser mayor a cero.");
        }
        if (inicial.signum() == 0 && cantidadMensuales == 0) {
            throw new IllegalArgumentException("El plan en cuotas necesita una cuota inicial o al menos una cuota mensual.");
        }
        if (primerVencimiento == null) {
            throw new IllegalArgumentException("La fecha del primer vencimiento es obligatoria.");
        }
        List<Cuota> cuotas = new ArrayList<>();
        if (inicial.signum() > 0) {
            cuotas.add(new Cuota(0, "Cuota inicial", primerVencimiento, inicial));
        }
        for (int i = 1; i <= cantidadMensuales; i++) {
            cuotas.add(new Cuota(i, "Cuota " + i, primerVencimiento.plusMonths(i), montoMensual));
        }
        return cuotas;
    }

    public static BigDecimal total(List<Cuota> cuotas) {
        return cuotas.stream().map(Cuota::monto).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Reparte lo pagado entre las cuotas, de la más antigua a la más nueva, y marca las vencidas a {@code hoy}. */
    public static List<CuotaConEstado> aplicarPagos(List<Cuota> cuotas, BigDecimal pagadoTotal, LocalDate hoy) {
        BigDecimal restante = pagadoTotal != null && pagadoTotal.signum() > 0 ? pagadoTotal : BigDecimal.ZERO;
        List<CuotaConEstado> resultado = new ArrayList<>();
        for (Cuota cuota : cuotas.stream().sorted(java.util.Comparator.comparingInt(Cuota::numero)).toList()) {
            BigDecimal aplicado = restante.min(cuota.monto());
            restante = restante.subtract(aplicado);
            BigDecimal saldo = cuota.monto().subtract(aplicado);
            Estado estado;
            if (saldo.signum() == 0) {
                estado = Estado.PAGADA;
            } else if (cuota.venceEn().isBefore(hoy)) {
                estado = Estado.VENCIDA;
            } else if (aplicado.signum() > 0) {
                estado = Estado.PARCIAL;
            } else {
                estado = Estado.PENDIENTE;
            }
            resultado.add(new CuotaConEstado(cuota, aplicado, saldo, estado));
        }
        return resultado;
    }

    public static long contarVencidas(List<CuotaConEstado> cuotas) {
        return cuotas.stream().filter(c -> c.estado() == Estado.VENCIDA).count();
    }
}
