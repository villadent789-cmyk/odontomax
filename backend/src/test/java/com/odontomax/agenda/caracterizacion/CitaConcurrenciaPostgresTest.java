package com.odontomax.agenda.caracterizacion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.odontomax.agenda.application.port.in.ProcesarExpiracionUseCase;
import org.springframework.http.MediaType;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * B-039: las operaciones que mueven dinero sobre una misma cita se serializan con un bloqueo pesimista de su fila.
 * N hilos lanzan a la vez la misma operación contra PostgreSQL real (pila completa) y solo una puede surtir efecto.
 */
@Testcontainers(disabledWithoutDocker = true)
class CitaConcurrenciaPostgresTest extends AgendaPostgresFixtures {

    private static final int HILOS = 6;
    private static final int CITAS = 25;

    @Autowired private ProcesarExpiracionUseCase expiracion;

    /** Lanza la misma petición desde HILOS hilos a la vez y devuelve los códigos HTTP obtenidos. */
    private List<Integer> enParalelo(Callable<Integer> peticion) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(HILOS);
        try {
            CountDownLatch listos = new CountDownLatch(HILOS);
            CountDownLatch salida = new CountDownLatch(1);
            List<Future<Integer>> futuros = new ArrayList<>();
            for (int i = 0; i < HILOS; i++) {
                futuros.add(pool.submit(() -> {
                    listos.countDown();
                    salida.await();
                    return peticion.call();
                }));
            }
            assertThat(listos.await(30, TimeUnit.SECONDS)).isTrue();
            salida.countDown();
            List<Integer> codigos = new ArrayList<>();
            for (Future<Integer> f : futuros) {
                codigos.add(f.get(60, TimeUnit.SECONDS));
            }
            return codigos;
        } finally {
            pool.shutdownNow();
        }
    }

    private UUID citaConAdelantoPagado(UUID paciente, int minuto) {
        UUID cita = insertarCita(paciente, null, SEDE_CHILCA_1, null, fechaUnica(), LocalTime.of(8 + minuto / 60 % 10, minuto % 60), 30,
                "CONFIRMADA_CON_ADELANTO");
        jdbc.update("UPDATE citas SET adelanto_requerido = true, adelanto_monto = 30.00, adelanto_estado = 'PAGADO', "
                + "adelanto_medio_pago = 'YAPE' WHERE id = ?", cita);
        return cita;
    }

    @Test
    @DisplayName("Cancelaciones simultaneas de la misma cita con adelanto pagado: un solo saldo a favor, una sola cancelacion y el resto recibe 400")
    void cancelacionesSimultaneasAcreditanUnaVez() throws Exception {
        UsuarioPrueba recepcion = crearUsuario("RECEPCION", SEDE_CHILCA_1);
        UUID paciente = crearPaciente(SEDE_CHILCA_1);
        int citasConDobleAcreditacion = 0;
        StringBuilder detalle = new StringBuilder();

        for (int n = 0; n < CITAS; n++) {
            UUID cita = citaConAdelantoPagado(paciente, n);
            List<Integer> codigos = enParalelo(() -> mockMvc.perform(patch("/citas/" + cita + "/cancelar")
                    .header("Authorization", recepcion.bearer()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"motivo\":\"Cancelacion simultanea de prueba\"}")).andReturn().getResponse().getStatus());

            Integer saldos = jdbc.queryForObject("SELECT count(*) FROM paciente_saldos_favor WHERE cita_origen_id = ?", Integer.class, cita);
            long exitos = codigos.stream().filter(c -> c == 200).count();
            if (saldos != 1 || exitos != 1) {
                citasConDobleAcreditacion++;
                detalle.append("cita ").append(n).append(": saldos=").append(saldos).append(" codigos=").append(codigos).append('\n');
            }
            assertThat(codigos).as("solo 200 o 400: " + codigos).allMatch(c -> c == 200 || c == 400 || c == 409);
            assertThat(estadoDe(cita)).isEqualTo("CANCELADA");
        }
        assertThat(citasConDobleAcreditacion).as("citas con acreditacion distinta de una (de " + CITAS + "):\n" + detalle).isZero();
    }

    @Test
    @DisplayName("Adelantos simultaneos sobre la misma cita: un solo registro de pago y el resto recibe 400")
    void adelantosSimultaneosRegistranUnaVez() throws Exception {
        UsuarioPrueba recepcion = crearUsuario("RECEPCION", SEDE_CHILCA_1);
        UUID paciente = crearPaciente(SEDE_CHILCA_1);
        int citasConDobleCobro = 0;
        StringBuilder detalle = new StringBuilder();

        for (int n = 0; n < CITAS; n++) {
            UUID cita = insertarCita(paciente, null, SEDE_CHILCA_1, null, fechaUnica(), LocalTime.of(8 + n / 60 % 10, n % 60), 30, "PENDIENTE_DE_PAGO");
            List<Integer> codigos = enParalelo(() -> mockMvc.perform(post("/citas/" + cita + "/adelanto")
                    .header("Authorization", recepcion.bearer()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"medioPago\":\"YAPE\",\"monto\":30,\"numeroOperacion\":\"123456\"}")).andReturn().getResponse().getStatus());

            Integer pagos = jdbc.queryForObject(
                    "SELECT count(*) FROM auditoria_log WHERE accion = 'REGISTRO_ADELANTO_MANUAL' AND detalle LIKE ?",
                    Integer.class, "%" + cita + "%");
            long exitos = codigos.stream().filter(c -> c == 200).count();
            if (pagos != 1 || exitos != 1) {
                citasConDobleCobro++;
                detalle.append("cita ").append(n).append(": pagos=").append(pagos).append(" codigos=").append(codigos).append('\n');
            }
            assertThat(estadoDe(cita)).isEqualTo("CONFIRMADA_CON_ADELANTO");
        }
        assertThat(citasConDobleCobro).as("citas con registro de pago distinto de uno (de " + CITAS + "):\n" + detalle).isZero();
    }

    @Test
    @DisplayName("R3: cancelar y PATCH /estado CANCELADA simultaneos sobre la misma cita: un solo saldo y un solo 200")
    void cancelarContraPatchEstadoCancelada() throws Exception {
        UsuarioPrueba recepcion = crearUsuario("RECEPCION", SEDE_CHILCA_1);
        UUID paciente = crearPaciente(SEDE_CHILCA_1);
        StringBuilder malas = new StringBuilder();
        for (int n = 0; n < CITAS; n++) {
            UUID cita = citaConAdelantoPagado(paciente, n);
            List<Callable<Integer>> tareas = new ArrayList<>();
            for (int h = 0; h < HILOS; h++) {
                boolean porEstado = h % 2 == 0;
                tareas.add(() -> mockMvc.perform(patch("/citas/" + cita + (porEstado ? "/estado" : "/cancelar"))
                        .header("Authorization", recepcion.bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(porEstado ? "{\"estado\":\"CANCELADA\",\"motivo\":\"Cancelacion por estado\"}"
                                : "{\"motivo\":\"Cancelacion directa\"}")).andReturn().getResponse().getStatus());
            }
            List<Integer> codigos = enParaleloMezcla(tareas);
            Integer saldos = jdbc.queryForObject("SELECT count(*) FROM paciente_saldos_favor WHERE cita_origen_id = ?", Integer.class, cita);
            if (saldos != 1 || codigos.stream().filter(c -> c == 200).count() != 1
                    || !codigos.stream().allMatch(c -> c == 200 || c == 400 || c == 409) || !"CANCELADA".equals(estadoDe(cita))) {
                malas.append("cita ").append(n).append(": saldos=").append(saldos).append(" codigos=").append(codigos).append('\n');
            }
        }
        assertThat(malas.toString()).as("citas con resultado incorrecto").isEmpty();
    }

    @Test
    @DisplayName("R3: cancelar y reprogramar simultaneos: la cita termina CANCELADA (nunca viva y cancelada a la vez) con un solo saldo")
    void cancelarContraReprogramar() throws Exception {
        UsuarioPrueba recepcion = crearUsuario("RECEPCION", SEDE_CHILCA_1);
        UUID paciente = crearPaciente(SEDE_CHILCA_1);
        StringBuilder malas = new StringBuilder();
        for (int n = 0; n < CITAS; n++) {
            UUID cita = citaConAdelantoPagado(paciente, n);
            String nuevaFecha = fechaUnica().toString();
            List<Callable<Integer>> tareas = new ArrayList<>();
            for (int h = 0; h < HILOS; h++) {
                boolean cancelar = h % 2 == 0;
                tareas.add(() -> mockMvc.perform(patch("/citas/" + cita + (cancelar ? "/cancelar" : "/reprogramar"))
                        .header("Authorization", recepcion.bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(cancelar ? "{\"motivo\":\"Cancelacion contra reprogramacion\"}"
                                : "{\"nuevaFecha\":\"" + nuevaFecha + "\",\"nuevoHorario\":\"15:00\",\"motivo\":\"Reprogramacion de prueba\"}"))
                        .andReturn().getResponse().getStatus());
            }
            List<Integer> codigos = enParaleloMezcla(tareas);
            Integer saldos = jdbc.queryForObject("SELECT count(*) FROM paciente_saldos_favor WHERE cita_origen_id = ?", Integer.class, cita);
            long cancelaciones200 = 0;
            for (int h = 0; h < HILOS; h++) {
                if (h % 2 == 0 && codigos.get(h) == 200) {
                    cancelaciones200++;
                }
            }
            if (saldos != 1 || cancelaciones200 != 1 || !"CANCELADA".equals(estadoDe(cita))
                    || !codigos.stream().allMatch(c -> c == 200 || c == 400 || c == 409)) {
                malas.append("cita ").append(n).append(": estado=").append(estadoDe(cita)).append(" saldos=").append(saldos)
                        .append(" codigos=").append(codigos).append('\n');
            }
        }
        assertThat(malas.toString()).as("citas con resultado incorrecto").isEmpty();
    }

    @Test
    @DisplayName("R3: adelanto y barrido de expiracion simultaneos: la cita pagada nunca queda EXPIRADA, y la expirada nunca queda pagada")
    void adelantoContraExpiracion() throws Exception {
        UsuarioPrueba recepcion = crearUsuario("RECEPCION", SEDE_CHILCA_1);
        UUID paciente = crearPaciente(SEDE_CHILCA_1);
        StringBuilder malas = new StringBuilder();
        for (int n = 0; n < CITAS; n++) {
            UUID cita = insertarCita(paciente, null, SEDE_CHILCA_1, null, fechaUnica(), LocalTime.of(8 + n / 60 % 10, n % 60), 30, "PENDIENTE_DE_PAGO");
            jdbc.update("UPDATE citas SET adelanto_requerido = true, adelanto_monto = 30.00, adelanto_estado = 'PENDIENTE', "
                    + "adelanto_limite_pago = now() - interval '5 minutes' WHERE id = ?", cita);
            List<Callable<Integer>> tareas = new ArrayList<>();
            for (int h = 0; h < HILOS; h++) {
                if (h % 2 == 0) {
                    tareas.add(() -> mockMvc.perform(post("/citas/" + cita + "/adelanto")
                            .header("Authorization", recepcion.bearer()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"medioPago\":\"YAPE\",\"monto\":30,\"numeroOperacion\":\"123456\"}"))
                            .andReturn().getResponse().getStatus());
                } else {
                    tareas.add(() -> {
                        expiracion.procesarCitasExpiradas();
                        return 0;
                    });
                }
            }
            List<Integer> codigos = enParaleloMezcla(tareas);
            String estado = estadoDe(cita);
            String adelanto = jdbc.queryForObject("SELECT adelanto_estado FROM citas WHERE id = ?", String.class, cita);
            Integer pagos = jdbc.queryForObject(
                    "SELECT count(*) FROM auditoria_log WHERE accion = 'REGISTRO_ADELANTO_MANUAL' AND detalle LIKE ?",
                    Integer.class, "%" + cita + "%");
            boolean pagada = "CONFIRMADA_CON_ADELANTO".equals(estado) && "PAGADO".equals(adelanto) && pagos == 1;
            boolean expirada = "EXPIRADA".equals(estado) && "PERDIDO".equals(adelanto) && pagos == 0;
            if (!pagada && !expirada) {
                malas.append("cita ").append(n).append(": estado=").append(estado).append(" adelanto=").append(adelanto)
                        .append(" pagos=").append(pagos).append(" codigos=").append(codigos).append('\n');
            }
        }
        assertThat(malas.toString()).as("citas en estado incoherente").isEmpty();
    }

    /** Lanza a la vez tareas distintas (una por hilo) y devuelve el resultado de cada una. */
    private List<Integer> enParaleloMezcla(List<Callable<Integer>> tareas) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tareas.size());
        try {
            CountDownLatch listos = new CountDownLatch(tareas.size());
            CountDownLatch salida = new CountDownLatch(1);
            List<Future<Integer>> futuros = new ArrayList<>();
            for (Callable<Integer> t : tareas) {
                futuros.add(pool.submit(() -> {
                    listos.countDown();
                    salida.await();
                    return t.call();
                }));
            }
            assertThat(listos.await(30, TimeUnit.SECONDS)).isTrue();
            salida.countDown();
            List<Integer> codigos = new ArrayList<>();
            for (Future<Integer> fu : futuros) {
                codigos.add(fu.get(60, TimeUnit.SECONDS));
            }
            return codigos;
        } finally {
            pool.shutdownNow();
        }
    }
}
