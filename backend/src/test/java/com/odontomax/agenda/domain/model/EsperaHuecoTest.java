package com.odontomax.agenda.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EsperaHuecoTest {

    private static EsperaHueco espera(UUID doctor, String motivo) {
        return new EsperaHueco(null, "sede-chilca-1", UUID.randomUUID(), "Luciana Meza", "987654321", doctor, motivo, null,
                EsperaHueco.ACTIVA, UUID.randomUUID(), Instant.now());
    }

    @Test
    @DisplayName("Sin doctor preferido acepta cualquier hueco; con doctor, solo los de ese doctor")
    void aceptaDoctor() {
        UUID doctorA = UUID.randomUUID();
        UUID doctorB = UUID.randomUUID();

        assertThat(espera(null, "Limpieza").aceptaDoctor(doctorA)).isTrue();
        assertThat(espera(doctorA, "Limpieza").aceptaDoctor(doctorA)).isTrue();
        assertThat(espera(doctorA, "Limpieza").aceptaDoctor(doctorB)).isFalse();
    }

    @Test
    @DisplayName("Una espera pasa de activa a ofrecida y de ahí a agendada; no se reabre una cancelada")
    void transiciones() {
        EsperaHueco e = espera(null, "Limpieza");

        e.cambiarEstado(EsperaHueco.OFRECIDA);
        e.cambiarEstado(EsperaHueco.ACTIVA);
        e.cambiarEstado(EsperaHueco.OFRECIDA);
        e.cambiarEstado(EsperaHueco.AGENDADA);
        assertThat(e.getEstado()).isEqualTo(EsperaHueco.AGENDADA);

        assertThatThrownBy(() -> e.cambiarEstado(EsperaHueco.ACTIVA)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("El motivo es obligatorio")
    void motivoObligatorio() {
        assertThatThrownBy(() -> espera(null, " ")).isInstanceOf(IllegalArgumentException.class);
    }
}
