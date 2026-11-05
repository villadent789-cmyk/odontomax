package com.odontomax.agenda.domain.model;

import com.odontomax.agenda.api.ConsultaEspecialistasModuloApi.MetaDoctor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EspecialistaMetaTest {

    @Test
    @DisplayName("Define meta y porcentaje válidos y permite borrarlos con nulo")
    void definirYBorrarMeta() {
        Especialista doctor = new Especialista();

        doctor.definirMeta(new BigDecimal("20000.00"), new BigDecimal("10.00"));
        assertThat(doctor.getMetaMensual()).isEqualByComparingTo("20000");
        assertThat(doctor.getComisionExcedentePorcentaje()).isEqualByComparingTo("10");

        doctor.definirMeta(null, null);
        assertThat(doctor.getMetaMensual()).isNull();
        assertThat(doctor.getComisionExcedentePorcentaje()).isNull();
    }

    @Test
    @DisplayName("Rechaza una meta negativa y un porcentaje fuera de 0 a 100")
    void rechazaValoresInvalidos() {
        Especialista doctor = new Especialista();

        assertThatThrownBy(() -> doctor.definirMeta(new BigDecimal("-1"), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> doctor.definirMeta(null, new BigDecimal("-0.01")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> doctor.definirMeta(null, new BigDecimal("100.01")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Comisión = max(0, cobrado − meta) × porcentaje / 100")
    void comisionSobreElExcedente() {
        MetaDoctor meta = new MetaDoctor(new BigDecimal("20000"), new BigDecimal("10"));

        assertThat(meta.comisionEstimada(new BigDecimal("25000"))).isEqualByComparingTo("500.00");
        assertThat(meta.comisionEstimada(new BigDecimal("20000"))).isEqualByComparingTo("0");
        assertThat(meta.comisionEstimada(new BigDecimal("14000"))).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Sin meta o sin porcentaje la comisión es nula, no cero")
    void sinMetaOSinPorcentajeLaComisionEsNula() {
        assertThat(new MetaDoctor(null, new BigDecimal("10")).comisionEstimada(new BigDecimal("999999"))).isNull();
        assertThat(new MetaDoctor(new BigDecimal("1000"), null).comisionEstimada(new BigDecimal("999999"))).isNull();
    }
}
