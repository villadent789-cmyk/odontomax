package com.odontomax.identidad.infrastructure.adapter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryBruteForceAdapterTest {

    private static final String CLAVE = "doctor@odontomax.pe|10.0.0.1";

    private final InMemoryBruteForceAdapter adapter = new InMemoryBruteForceAdapter();

    private void fallar(int veces) {
        for (int i = 0; i < veces; i++) {
            adapter.recordFailedAttempt(CLAVE);
        }
    }

    @Test
    @DisplayName("Sin intentos registrados la clave no está bloqueada")
    void sinIntentos() {
        assertThat(adapter.minutosDeBloqueoRestantes(CLAVE)).isEmpty();
    }

    @Test
    @DisplayName("4 fallos no bloquean; el 5.º bloquea 15 minutos (14 o 15 según el redondeo)")
    void bloqueaAlQuintoFallo() {
        fallar(4);
        assertThat(adapter.minutosDeBloqueoRestantes(CLAVE)).isEmpty();

        fallar(1);
        assertThat(adapter.minutosDeBloqueoRestantes(CLAVE)).isPresent();
        assertThat(adapter.minutosDeBloqueoRestantes(CLAVE).getAsLong()).isBetween(14L, 15L);
    }

    @Test
    @DisplayName("resetAttempts elimina el contador de la clave")
    void resetLimpia() {
        fallar(5);
        adapter.resetAttempts(CLAVE);

        assertThat(adapter.minutosDeBloqueoRestantes(CLAVE)).isEmpty();
        fallar(4);
        assertThat(adapter.minutosDeBloqueoRestantes(CLAVE)).isEmpty();
    }

    @Test
    @DisplayName("Las claves son independientes (otra IP no queda bloqueada)")
    void clavesIndependientes() {
        fallar(5);

        assertThat(adapter.minutosDeBloqueoRestantes("doctor@odontomax.pe|10.0.0.2")).isEmpty();
    }

    @Test
    @DisplayName("purgeExpiredAttempts no libera bloqueos vigentes")
    void purgaNoLiberaVigentes() {
        fallar(5);

        adapter.purgeExpiredAttempts();

        assertThat(adapter.minutosDeBloqueoRestantes(CLAVE)).isPresent();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("reservarIntento (BL-2): 50 hilos concurrentes sobre la misma clave reservan exactamente 5 y luego está bloqueada")
    void reservarIntentoAtomico() throws Exception {
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(50);
        java.util.concurrent.CountDownLatch salida = new java.util.concurrent.CountDownLatch(1);
        try {
            java.util.List<java.util.concurrent.Future<java.util.OptionalLong>> futuros = new java.util.ArrayList<>();
            for (int i = 0; i < 50; i++) {
                futuros.add(pool.submit(() -> {
                    salida.await();
                    return adapter.reservarIntento("concurrente|1.1.1.1");
                }));
            }
            salida.countDown();
            int reservados = 0;
            for (java.util.concurrent.Future<java.util.OptionalLong> f : futuros) {
                if (f.get().isEmpty()) {
                    reservados++;
                }
            }
            org.assertj.core.api.Assertions.assertThat(reservados).isEqualTo(5);
            org.assertj.core.api.Assertions.assertThat(adapter.minutosDeBloqueoRestantes("concurrente|1.1.1.1")).isPresent();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @org.junit.jupiter.api.DisplayName("reservarIntento con máximo propio (revisión 2, obs. 3): 10 reservas y la 11.ª bloqueada")
    void reservarIntentoConMaximoPropio() {
        for (int i = 0; i < 10; i++) {
            org.assertj.core.api.Assertions.assertThat(adapter.reservarIntento("login-2fa|ana@x.pe", 10)).isEmpty();
        }
        org.assertj.core.api.Assertions.assertThat(adapter.reservarIntento("login-2fa|ana@x.pe", 10)).isPresent();
    }

    @Test
    @DisplayName("Capacidad máxima acota el mapa y rechaza desbordamiento")
    void respetaCapacidadMaxima() {
        InMemoryBruteForceAdapter acotado = new InMemoryBruteForceAdapter(2);
        acotado.recordFailedAttempt("ip1");
        acotado.recordFailedAttempt("ip2");
        acotado.recordFailedAttempt("ip3");

        assertThat(acotado.reservarIntento("ip3")).isPresent();
        assertThat(acotado.reservarIntento("ip1")).isEmpty();
    }
}
