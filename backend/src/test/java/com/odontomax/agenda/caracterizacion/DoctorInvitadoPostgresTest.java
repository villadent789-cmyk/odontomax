package com.odontomax.agenda.caracterizacion;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Doctor invitado: viene por cita previa, sin cuenta, sin horario semanal y sin sillón fijo. Se agenda en la fecha y hora
 * acordadas, en el primer sillón libre de la sede, y solo en las sedes que tiene asignadas.
 */
@Testcontainers(disabledWithoutDocker = true)
class DoctorInvitadoPostgresTest extends AgendaPostgresFixtures {

    private String sede;
    private UsuarioPrueba recepcion;
    private UUID paciente;
    private UUID fijo;
    private UUID invitado;
    private LocalDate fecha;

    @BeforeEach
    void preparar() {
        sede = "sede-inv-" + tag();
        insertarSede(sede, "Invitado " + sede, true, 3);
        recepcion = crearUsuario("RECEPCION", sede);
        paciente = crearPaciente(sede);
        fijo = crearEspecialista(null, sede);
        asignarSillon(fijo, sede, 1);
        invitado = crearEspecialista(null, sede);
        jdbc.update("UPDATE especialistas SET invitado = true WHERE id = ?", invitado);
        jdbc.update("DELETE FROM especialista_horarios WHERE especialista_id = ?", invitado);
        fecha = fechaUnica();
    }

    private MvcResult agendar(UUID doctor, String sedeId, LocalTime hora) throws Exception {
        String cuerpo = "{\"pacienteId\":\"" + paciente + "\",\"doctorId\":\"" + doctor + "\",\"fecha\":\"" + fecha
                + "\",\"hora\":\"" + hora + "\",\"duracionMinutos\":30,\"motivoConsulta\":\"Implante\"}";
        return mockMvc.perform(post("/citas").header("Authorization", recepcion.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andReturn();
    }

    @Test
    @DisplayName("El invitado se agenda sin horario ni sillón fijo y recibe un sillón libre distinto del que ocupa otro doctor")
    void seAgendaSinHorarioNiSillonFijo() throws Exception {
        insertarCita(paciente, fijo, sede, 1, fecha, LocalTime.of(15, 0), 30, "PROGRAMADA");

        MvcResult r = agendar(invitado, sede, LocalTime.of(15, 0));

        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(201);
        JsonNode cita = leer(r);
        assertThat(cita.get("sillonNumero").asInt()).isNotEqualTo(1);
    }

    @Test
    @DisplayName("La disponibilidad del invitado ofrece las horas de atención de la clínica aunque no tenga horario")
    void disponibilidadSinHorario() throws Exception {
        MvcResult r = mockMvc.perform(get("/especialistas/" + invitado + "/disponibilidad")
                .param("fecha", fecha.toString()).param("sedeId", sede)
                .header("Authorization", recepcion.bearer())).andReturn();

        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        JsonNode d = leer(r);
        assertThat(d.get("atiendeEseDia").asBoolean()).isTrue();
        assertThat(d.get("slots").size()).isGreaterThan(0);
    }

    @Test
    @DisplayName("El invitado no se agenda en una sede que no tiene asignada")
    void soloEnSusSedes() throws Exception {
        String otra = "sede-inv-otra-" + tag();
        insertarSede(otra, "Otra " + otra, true, 3);
        UsuarioPrueba recepcionOtra = crearUsuario("RECEPCION", otra);
        UUID pacienteOtra = crearPaciente(otra);
        String cuerpo = "{\"pacienteId\":\"" + pacienteOtra + "\",\"doctorId\":\"" + invitado + "\",\"fecha\":\"" + fecha
                + "\",\"hora\":\"10:00\",\"duracionMinutos\":30,\"motivoConsulta\":\"Implante\"}";

        MvcResult r = mockMvc.perform(post("/citas").header("Authorization", recepcionOtra.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andReturn();

        assertThat(r.getResponse().getStatus()).isEqualTo(409);
    }

    private MvcResult marcarAtendida(UUID cita, String montoHonorario) throws Exception {
        String cuerpo = "{\"estado\":\"ATENDIDA\"" + (montoHonorario != null ? ",\"montoHonorario\":\"" + montoHonorario + "\"" : "") + "}";
        return mockMvc.perform(patch("/citas/" + cita + "/estado").header("Authorization", recepcion.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andReturn();
    }

    @Test
    @DisplayName("Atender con un invitado exige el monto de sus honorarios")
    void exigeElMonto() throws Exception {
        UUID cita = insertarCita(paciente, invitado, sede, 2, fecha, LocalTime.of(11, 0), 30, "EN_ATENCION");

        MvcResult r = marcarAtendida(cita, null);

        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        assertThat(estadoDe(cita)).isEqualTo("EN_ATENCION");
    }

    @Test
    @DisplayName("Con la caja cerrada no se puede finalizar la atención del invitado: la cita sigue en atención y no hay egreso")
    void conCajaCerradaNoDejaFinalizar() throws Exception {
        UUID cita = insertarCita(paciente, invitado, sede, 2, fecha, LocalTime.of(11, 0), 30, "EN_ATENCION");

        MvcResult r = marcarAtendida(cita, "100.00");

        assertThat(r.getResponse().getStatus()).isEqualTo(409);
        assertThat(estadoDe(cita)).isEqualTo("EN_ATENCION");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM caja_movimientos WHERE referencia_id = ?", Integer.class, cita)).isZero();
    }

    @Test
    @DisplayName("Con la caja abierta, finalizar la atención del invitado registra su pago como egreso de caja")
    void conCajaAbiertaRegistraElEgreso() throws Exception {
        jdbc.update("INSERT INTO caja_sesiones (sede_id, abierta_por) VALUES (?, ?)", sede, recepcion.id());
        UUID cita = insertarCita(paciente, invitado, sede, 2, fecha, LocalTime.of(11, 0), 30, "EN_ATENCION");

        MvcResult r = marcarAtendida(cita, "150.00");

        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(200);
        assertThat(estadoDe(cita)).isEqualTo("ATENDIDA");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM caja_movimientos WHERE referencia_id = ? AND tipo = 'EGRESO' AND monto = 150.00",
                Integer.class, cita)).isEqualTo(1);
    }

    @Test
    @DisplayName("Recepción no paga en efectivo más del tope sin un administrador")
    void respetaElTopeDeRecepcion() throws Exception {
        jdbc.update("INSERT INTO caja_sesiones (sede_id, abierta_por) VALUES (?, ?)", sede, recepcion.id());
        UUID cita = insertarCita(paciente, invitado, sede, 2, fecha, LocalTime.of(11, 0), 30, "EN_ATENCION");

        MvcResult r = marcarAtendida(cita, "5000.00");

        assertThat(r.getResponse().getStatus()).isEqualTo(403);
        assertThat(estadoDe(cita)).isEqualTo("EN_ATENCION");
    }

    @Test
    @DisplayName("Un doctor fijo se sigue atendiendo sin monto de honorarios")
    void elDoctorFijoNoPide() throws Exception {
        UUID cita = insertarCita(paciente, fijo, sede, 1, fecha, LocalTime.of(12, 0), 30, "EN_ATENCION");

        MvcResult r = marcarAtendida(cita, null);

        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        assertThat(estadoDe(cita)).isEqualTo("ATENDIDA");
    }
}
