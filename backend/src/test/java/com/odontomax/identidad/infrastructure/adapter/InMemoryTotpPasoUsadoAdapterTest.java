package com.odontomax.identidad.infrastructure.adapter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryTotpPasoUsadoAdapterTest {

    private final InMemoryTotpPasoUsadoAdapter adapter = new InMemoryTotpPasoUsadoAdapter();

    @Test
    @DisplayName("Rechaza el mismo paso y los anteriores; acepta los posteriores; por usuario")
    void pasosPorUsuario() {
        UUID ana = UUID.randomUUID();
        UUID beto = UUID.randomUUID();
        assertThat(adapter.esReutilizacion(ana, 100)).isFalse();
        assertThat(adapter.esReutilizacion(ana, 100)).isTrue();
        assertThat(adapter.esReutilizacion(ana, 99)).isTrue();
        assertThat(adapter.esReutilizacion(beto, 100)).isFalse();
        assertThat(adapter.esReutilizacion(ana, 101)).isFalse();
        assertThat(adapter.esReutilizacion(ana, 101)).isTrue();
    }

    @Test
    @DisplayName("olvidar: tras un secreto nuevo vuelve a aceptar cualquier paso")
    void olvidar() {
        UUID ana = UUID.randomUUID();
        assertThat(adapter.esReutilizacion(ana, 100)).isFalse();
        adapter.olvidar(ana);
        assertThat(adapter.esReutilizacion(ana, 100)).isFalse();
    }
}
