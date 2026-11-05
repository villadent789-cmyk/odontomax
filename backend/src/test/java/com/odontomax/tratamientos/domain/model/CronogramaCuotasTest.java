package com.odontomax.tratamientos.domain.model;

import com.odontomax.tratamientos.domain.model.CronogramaCuotas.Cuota;
import com.odontomax.tratamientos.domain.model.CronogramaCuotas.CuotaConEstado;
import com.odontomax.tratamientos.domain.model.CronogramaCuotas.Estado;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CronogramaCuotasTest {

    private static final LocalDate INICIO = LocalDate.of(2026, 10, 1);

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    @Test
    @DisplayName("Genera la cuota inicial el primer día y una cuota mensual cada mes")
    void generaElCronograma() {
        List<Cuota> cuotas = CronogramaCuotas.generar(bd("600"), bd("120"), 3, INICIO);

        assertThat(cuotas).extracting(Cuota::numero).containsExactly(0, 1, 2, 3);
        assertThat(cuotas).extracting(Cuota::venceEn)
                .containsExactly(INICIO, INICIO.plusMonths(1), INICIO.plusMonths(2), INICIO.plusMonths(3));
        assertThat(CronogramaCuotas.total(cuotas)).isEqualByComparingTo("960");
    }

    @Test
    @DisplayName("Sin cuota inicial el cronograma empieza en la cuota 1")
    void sinCuotaInicial() {
        List<Cuota> cuotas = CronogramaCuotas.generar(BigDecimal.ZERO, bd("150"), 2, INICIO);

        assertThat(cuotas).extracting(Cuota::numero).containsExactly(1, 2);
    }

    @Test
    @DisplayName("Rechaza cronogramas sin sentido")
    void rechazaDatosInvalidos() {
        assertThatThrownBy(() -> CronogramaCuotas.generar(BigDecimal.ZERO, bd("100"), 0, INICIO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CronogramaCuotas.generar(bd("100"), BigDecimal.ZERO, 3, INICIO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CronogramaCuotas.generar(bd("100"), bd("50"), CronogramaCuotas.MAX_CUOTAS_MENSUALES + 1, INICIO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CronogramaCuotas.generar(bd("100"), bd("50"), 2, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Un cobro se aplica a la cuota más antigua y el sobrante pasa a la siguiente")
    void repartoDeCobros() {
        List<Cuota> cuotas = CronogramaCuotas.generar(bd("600"), bd("120"), 2, INICIO);

        List<CuotaConEstado> r = CronogramaCuotas.aplicarPagos(cuotas, bd("660"), INICIO.minusDays(1));

        assertThat(r.get(0).estado()).isEqualTo(Estado.PAGADA);
        assertThat(r.get(1).pagado()).isEqualByComparingTo("60");
        assertThat(r.get(1).saldo()).isEqualByComparingTo("60");
        assertThat(r.get(1).estado()).isEqualTo(Estado.PARCIAL);
        assertThat(r.get(2).estado()).isEqualTo(Estado.PENDIENTE);
    }

    @Test
    @DisplayName("Con saldo y fecha pasada la cuota queda vencida; pagada nunca lo está")
    void cuotasVencidas() {
        List<Cuota> cuotas = CronogramaCuotas.generar(bd("600"), bd("120"), 2, INICIO);

        List<CuotaConEstado> r = CronogramaCuotas.aplicarPagos(cuotas, bd("600"), INICIO.plusMonths(1).plusDays(1));

        assertThat(r.get(0).estado()).isEqualTo(Estado.PAGADA);
        assertThat(r.get(1).estado()).isEqualTo(Estado.VENCIDA);
        assertThat(r.get(2).estado()).isEqualTo(Estado.PENDIENTE);
        assertThat(CronogramaCuotas.contarVencidas(r)).isEqualTo(1);
    }

    @Test
    @DisplayName("Anular un cobro vuelve a dejar pendientes las cuotas: el reparto depende solo del total pagado")
    void sinPagosTodoPendiente() {
        List<Cuota> cuotas = CronogramaCuotas.generar(bd("600"), bd("120"), 1, INICIO);

        List<CuotaConEstado> r = CronogramaCuotas.aplicarPagos(cuotas, null, INICIO.minusDays(1));

        assertThat(r).allSatisfy(c -> assertThat(c.estado()).isEqualTo(Estado.PENDIENTE));
        assertThat(r).allSatisfy(c -> assertThat(c.pagado()).isEqualByComparingTo("0"));
    }
}
