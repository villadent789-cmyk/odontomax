package com.odontomax.agenda.caracterizacion;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * B-038 con pila completa: POST /citas/{id}/adelanto solo se admite en los estados donde el flujo de negocio registra
 * un adelanto; una cita final (o ya en sala, o que ya tiene su adelanto) responde 400 y no mueve dinero ni sesiones.
 */
@Testcontainers(disabledWithoutDocker = true)
class CitaAdelantoEstadoPostgresTest extends AgendaPostgresFixtures {

    private static final String CUERPO = "{\"medioPago\":\"YAPE\",\"monto\":30,\"numeroOperacion\":\"123456\"}";

    private MvcResult adelanto(UsuarioPrueba u, UUID cita) throws Exception {
        return mockMvc.perform(post("/citas/" + cita + "/adelanto").header("Authorization", u.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(CUERPO)).andReturn();
    }

    private int movimientosDeDinero(UUID cita, UUID paciente) {
        Integer auditorias = jdbc.queryForObject(
                "SELECT count(*) FROM auditoria_log WHERE accion = 'REGISTRO_ADELANTO_MANUAL' AND detalle LIKE ?",
                Integer.class, "%" + cita + "%");
        Integer saldos = jdbc.queryForObject("SELECT count(*) FROM paciente_saldos_favor WHERE paciente_id = ?", Integer.class, paciente);
        return auditorias + saldos;
    }

    @Test
    @DisplayName("Estados finales, EN_SALA o ya con adelanto: 400 con mensaje claro, sin dinero, sin sesión sumada y la cita no cambia")
    void estadosQueNoAdmitenAdelanto() throws Exception {
        UsuarioPrueba recepcion = crearUsuario("RECEPCION", SEDE_CHILCA_1);
        UUID paciente = crearPaciente(SEDE_CHILCA_1);
        UUID plan = UUID.randomUUID();
        jdbc.update("INSERT INTO tratamientos (id, paciente_id, sede_id, nombre, costo_total, estado, total_sesiones, sesiones_realizadas) "
                + "VALUES (?, ?, ?, 'Ortodoncia', 1500, 'EN_PROCESO', 5, 2)", plan, paciente, SEDE_CHILCA_1);
        LocalDate fecha = fechaUnica();

        int hora = 8;
        for (String estado : new String[]{"ATENDIDA", "CANCELADA", "NO_ASISTIO", "EXPIRADA", "EN_SALA", "CONFIRMADA_CON_ADELANTO"}) {
            UUID cita = insertarCita(paciente, null, SEDE_CHILCA_1, null, fecha, LocalTime.of(hora++, 0), 30, estado);
            jdbc.update("UPDATE citas SET tratamiento_id = ? WHERE id = ?", plan, cita);
            // R1: CONFIRMADA_CON_ADELANTO solo rechaza el adelanto si ya esta cobrado (PAGADO)
            String adelantoEsperado = "CONFIRMADA_CON_ADELANTO".equals(estado) ? "PAGADO" : "NO_APLICA";
            jdbc.update("UPDATE citas SET adelanto_estado = ? WHERE id = ?", adelantoEsperado, cita);

            MvcResult r = adelanto(recepcion, cita);
            assertThat(r.getResponse().getStatus()).as(estado + ": " + r.getResponse().getContentAsString()).isEqualTo(400);
            JsonNode cuerpo = leer(r);
            assertThat(cuerpo.get("message").asText()).as(estado).contains("adelanto").contains(estado);

            assertThat(estadoDe(cita)).as(estado).isEqualTo(estado);
            assertThat(jdbc.queryForObject("SELECT adelanto_estado FROM citas WHERE id = ?", String.class, cita)).as(estado).isEqualTo(adelantoEsperado);
            assertThat(jdbc.queryForObject("SELECT notas FROM citas WHERE id = ?", String.class, cita)).as(estado).isNull();
            assertThat(movimientosDeDinero(cita, paciente)).as(estado + " movimientos de dinero").isZero();
        }
        assertThat(jdbc.queryForObject("SELECT sesiones_realizadas FROM tratamientos WHERE id = ?", Integer.class, plan)).isEqualTo(2);
    }

    @Test
    @DisplayName("Revivir una cita ATENDIDA con un adelanto ya no es posible: no se puede volver a atender ni sumar otra sesión al plan")
    void noSePuedeRevivirUnaCitaAtendida() throws Exception {
        UsuarioPrueba recepcion = crearUsuario("RECEPCION", SEDE_CHILCA_1);
        UUID paciente = crearPaciente(SEDE_CHILCA_1);
        UUID plan = UUID.randomUUID();
        jdbc.update("INSERT INTO tratamientos (id, paciente_id, sede_id, nombre, costo_total, estado, total_sesiones, sesiones_realizadas) "
                + "VALUES (?, ?, ?, 'Ortodoncia', 1500, 'EN_PROCESO', 5, 3)", plan, paciente, SEDE_CHILCA_1);
        UUID cita = insertarCita(paciente, null, SEDE_CHILCA_1, null, fechaUnica(), LocalTime.of(9, 0), 30, "ATENDIDA");
        jdbc.update("UPDATE citas SET tratamiento_id = ? WHERE id = ?", plan, cita);

        assertThat(adelanto(recepcion, cita).getResponse().getStatus()).isEqualTo(400);
        // Si la cita se hubiera revivido, estos pasos la devolverían a ATENDIDA y sumarían una segunda sesión.
        for (String estado : new String[]{"EN_SALA", "ATENDIDA"}) {
            MvcResult r = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/citas/" + cita + "/estado")
                    .header("Authorization", recepcion.bearer()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"estado\":\"" + estado + "\"}")).andReturn();
            assertThat(r.getResponse().getStatus()).as(estado).isEqualTo(409);
        }
        assertThat(estadoDe(cita)).isEqualTo("ATENDIDA");
        assertThat(jdbc.queryForObject("SELECT sesiones_realizadas FROM tratamientos WHERE id = ?", Integer.class, plan)).isEqualTo(3);
        assertThat(movimientosDeDinero(cita, paciente)).isZero();
    }

    @Test
    @DisplayName("R1: tras PATCH /estado a CONFIRMADA_CON_ADELANTO (sin cobro) el adelanto aun se puede registrar una vez; el segundo da 400")
    void adelantoTrasConfirmarPorEstadoSinCobro() throws Exception {
        UsuarioPrueba recepcion = crearUsuario("RECEPCION", SEDE_CHILCA_1);
        UUID paciente = crearPaciente(SEDE_CHILCA_1);
        UUID cita = insertarCita(paciente, null, SEDE_CHILCA_1, null, fechaUnica(), LocalTime.of(9, 0), 30, "PENDIENTE_DE_PAGO");
        jdbc.update("UPDATE citas SET adelanto_requerido = true, adelanto_monto = 30.00, adelanto_estado = 'PENDIENTE' WHERE id = ?", cita);

        MvcResult patch = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/citas/" + cita + "/estado")
                .header("Authorization", recepcion.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"CONFIRMADA_CON_ADELANTO\"}")).andReturn();
        assertThat(patch.getResponse().getStatus()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT adelanto_estado FROM citas WHERE id = ?", String.class, cita)).isEqualTo("PENDIENTE");

        MvcResult r = adelanto(recepcion, cita);
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT adelanto_estado FROM citas WHERE id = ?", String.class, cita)).isEqualTo("PAGADO");
        assertThat(movimientosDeDinero(cita, paciente)).isEqualTo(1);

        assertThat(adelanto(recepcion, cita).getResponse().getStatus()).isEqualTo(400);
        assertThat(movimientosDeDinero(cita, paciente)).isEqualTo(1);
    }

    @Test
    @DisplayName("Los flujos reales siguen funcionando: PENDIENTE_DE_PAGO, PENDIENTE_WEB, PROGRAMADA y CONFIRMADA pasan a CONFIRMADA_CON_ADELANTO con un solo registro de pago")
    void estadosQueAdmitenAdelanto() throws Exception {
        UsuarioPrueba recepcion = crearUsuario("RECEPCION", SEDE_CHILCA_1);
        UUID paciente = crearPaciente(SEDE_CHILCA_1);
        LocalDate fecha = fechaUnica();

        int hora = 8;
        for (String estado : new String[]{"PENDIENTE_DE_PAGO", "PENDIENTE_WEB", "PROGRAMADA", "CONFIRMADA"}) {
            UUID cita = insertarCita(paciente, null, SEDE_CHILCA_1, null, fecha, LocalTime.of(hora++, 0), 30, estado);

            MvcResult r = adelanto(recepcion, cita);
            assertThat(r.getResponse().getStatus()).as(estado + ": " + r.getResponse().getContentAsString()).isEqualTo(200);
            assertThat(leer(r).get("estado").asText()).as(estado).isEqualTo("CONFIRMADA_CON_ADELANTO");
            assertThat(estadoDe(cita)).as(estado).isEqualTo("CONFIRMADA_CON_ADELANTO");
            assertThat(jdbc.queryForObject("SELECT adelanto_estado FROM citas WHERE id = ?", String.class, cita)).as(estado).isEqualTo("PAGADO");
            assertThat(movimientosDeDinero(cita, paciente)).as(estado).isEqualTo(1);

            // un segundo adelanto sobre la misma cita ya no se admite
            assertThat(adelanto(recepcion, cita).getResponse().getStatus()).as(estado + " repetido").isEqualTo(400);
            assertThat(movimientosDeDinero(cita, paciente)).as(estado + " repetido").isEqualTo(1);
        }
    }
}
