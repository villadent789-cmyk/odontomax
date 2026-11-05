package com.odontomax.agenda.infrastructure.adapter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryRateLimitAdapterTest {

    private final InMemoryRateLimitAdapter adaptador = new InMemoryRateLimitAdapter();

    @Test
    @DisplayName("Ventana corta: permite hasta el máximo y bloquea el siguiente")
    void ventanaCorta() {
        for (int i = 0; i < 3; i++) {
            assertFalse(adaptador.isRateLimited("a", 3));
        }
        assertTrue(adaptador.isRateLimited("a", 3));
    }

    @Test
    @DisplayName("Tope diario: cuenta aparte de la ventana corta y cada clave tiene su propio contador")
    void ventanaLargaPorClave() {
        assertFalse(adaptador.isRateLimited("tel", 2, 86_400));
        assertFalse(adaptador.isRateLimited("tel", 2, 86_400));
        assertTrue(adaptador.isRateLimited("tel", 2, 86_400));
        // Otra clave no se ve afectada
        assertFalse(adaptador.isRateLimited("otro", 2, 86_400));
    }

    @Test
    @DisplayName("Una ventana de 0 segundos vence enseguida y el contador vuelve a empezar")
    void laVentanaVence() throws InterruptedException {
        assertFalse(adaptador.isRateLimited("x", 1, 0));
        Thread.sleep(5);
        assertFalse(adaptador.isRateLimited("x", 1, 0));
    }
}
