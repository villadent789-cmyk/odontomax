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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

/** B-028 con pila completa: PATCH /citas/{id}/estado respeta la máquina de estados y responde 400 con un mensaje claro. */
@Testcontainers(disabledWithoutDocker = true)
class CitaTransicionesPostgresTest extends AgendaPostgresFixtures {

    private MvcResult cambiar(UsuarioPrueba u, UUID cita, String cuerpo) throws Exception {
        return mockMvc.perform(patch("/citas/" + cita + "/estado").header("Authorization", u.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andReturn();
    }

    @Test
    @DisplayName("Desde un estado final o saltando el flujo: 400 con mensaje claro y la cita no cambia")
    void transicionesInvalidas400() throws Exception {
        UsuarioPrueba recepcion = crearUsuario("RECEPCION", SEDE_CHILCA_1);
        UUID paciente = crearPaciente(SEDE_CHILCA_1);
        LocalDate fecha = fechaUnica();
        UUID atendida = insertarCita(paciente, null, SEDE_CHILCA_1, null, fecha, LocalTime.of(9, 0), 30, "ATENDIDA");
        UUID cancelada = insertarCita(paciente, null, SEDE_CHILCA_1, null, fecha, LocalTime.of(10, 0), 30, "CANCELADA");
        UUID programada = insertarCita(paciente, null, SEDE_CHILCA_1, null, fecha, LocalTime.of(11, 0), 30, "PROGRAMADA");

        MvcResult r = cambiar(recepcion, atendida, "{\"estado\":\"PROGRAMADA\"}");
        assertThat(r.getResponse().getStatus()).isEqualTo(409);
        JsonNode cuerpo = leer(r);
        assertThat(cuerpo.get("message").asText()).isEqualTo("No se puede pasar una cita de ATENDIDA a PROGRAMADA.");

        assertThat(cambiar(recepcion, cancelada, "{\"estado\":\"CONFIRMADA\"}").getResponse().getStatus()).isEqualTo(409);
        assertThat(cambiar(recepcion, programada, "{\"estado\":\"ATENDIDA\"}").getResponse().getStatus()).isEqualTo(409);
        assertThat(cambiar(recepcion, programada, "{\"estado\":\"PROGRAMADA\"}").getResponse().getStatus()).isEqualTo(409);

        assertThat(estadoDe(atendida)).isEqualTo("ATENDIDA");
        assertThat(estadoDe(cancelada)).isEqualTo("CANCELADA");
        assertThat(estadoDe(programada)).isEqualTo("PROGRAMADA");
    }

    @Test
    @DisplayName("El flujo normal sigue funcionando y el motivo de una transición que no cancela no llena motivo_cancelacion")
    void flujoNormal() throws Exception {
        UsuarioPrueba recepcion = crearUsuario("RECEPCION", SEDE_CHILCA_1);
        UUID paciente = crearPaciente(SEDE_CHILCA_1);
        UUID cita = insertarCita(paciente, null, SEDE_CHILCA_1, null, fechaUnica(), LocalTime.of(9, 0), 30, "PENDIENTE_WEB");

        for (String estado : new String[]{"PROGRAMADA", "CONFIRMADA", "EN_SALA", "EN_ATENCION", "ATENDIDA"}) {
            MvcResult r = cambiar(recepcion, cita, "{\"estado\":\"" + estado + "\",\"motivo\":\"Paso a " + estado + "\"}");
            assertThat(r.getResponse().getStatus()).as(estado + ": " + r.getResponse().getContentAsString()).isEqualTo(200);
        }
        assertThat(estadoDe(cita)).isEqualTo("ATENDIDA");
        assertThat(jdbc.queryForObject("SELECT motivo_cancelacion FROM citas WHERE id = ?", String.class, cita)).isNull();
    }
}
