package com.odontomax.identidad.infrastructure.adapter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryLogin2faPendienteAdapterTest {

    private final InMemoryLogin2faPendienteAdapter adapter = new InMemoryLogin2faPendienteAdapter();

    @Test
    @DisplayName("Reserva hasta el máximo por clave; después rechaza; las claves son independientes")
    void reservaHastaElMaximo() {
        for (int i = 0; i < 5; i++) {
            assertThat(adapter.intentoRechazado("a", 5)).isFalse();
        }
        assertThat(adapter.intentoRechazado("a", 5)).isTrue();
        assertThat(adapter.intentoRechazado("b", 5)).isFalse();
    }

    @Test
    @DisplayName("invalidar: rechaza los intentos siguientes e informa si ya lo estaba")
    void invalidar() {
        assertThat(adapter.invalidar("x")).isFalse();
        assertThat(adapter.intentoRechazado("x", 5)).isTrue();
        assertThat(adapter.invalidar("x")).isTrue();
    }

    @Test
    @DisplayName("Concurrencia: 50 hilos sobre la misma clave reservan exactamente 5 intentos")
    void reservaAtomica() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(50);
        CountDownLatch salida = new CountDownLatch(1);
        try {
            List<Future<Boolean>> futuros = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                Callable<Boolean> tarea = () -> {
                    salida.await();
                    return adapter.intentoRechazado("c", 5);
                };
                futuros.add(pool.submit(tarea));
            }
            salida.countDown();
            int aceptados = 0;
            for (Future<Boolean> f : futuros) {
                if (!f.get()) {
                    aceptados++;
                }
            }
            assertThat(aceptados).isEqualTo(5);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("Revisión obs. 5: con el mapa lleno, los logins pendientes nuevos se rechazan (falla cerrado)")
    void topeDeEntradas() {
        InMemoryLogin2faPendienteAdapter lleno = new InMemoryLogin2faPendienteAdapter(1);
        assertThat(lleno.intentoRechazado("t1", 5)).isFalse();
        assertThat(lleno.intentoRechazado("t2", 5)).isTrue();
        assertThat(lleno.intentoRechazado("t1", 5)).isFalse();
    }

    @Test
    @DisplayName("intentosReservados (revisión 2, obs. 7): cuenta las reservas aceptadas, no las rechazadas")
    void intentosReservados() {
        assertThat(adapter.intentosReservados("z")).isZero();
        for (int i = 0; i < 7; i++) {
            adapter.intentoRechazado("z", 5);
        }
        assertThat(adapter.intentosReservados("z")).isEqualTo(5);
    }
}
