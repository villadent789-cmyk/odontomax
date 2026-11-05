package com.odontomax.agenda.caracterizacion;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

/** R2: si otra transaccion retiene el bloqueo de la cita, la operacion espera un maximo y responde 409 sin colgar la conexion. */
@Testcontainers(disabledWithoutDocker = true)
class CitaBloqueoTimeoutPostgresTest extends AgendaPostgresFixtures {

    @Test
    @DisplayName("Con la fila de la cita bloqueada por otra transaccion, cancelar responde 409 con mensaje claro tras el tiempo maximo y luego funciona")
    void bloqueoRetenidoDa409() throws Exception {
        UsuarioPrueba recepcion = crearUsuario("RECEPCION", SEDE_CHILCA_1);
        UUID paciente = crearPaciente(SEDE_CHILCA_1);
        UUID cita = insertarCita(paciente, null, SEDE_CHILCA_1, null, fechaUnica(), LocalTime.of(9, 0), 30, "PROGRAMADA");

        try (Connection retenedora = jdbc.getDataSource().getConnection()) {
            retenedora.setAutoCommit(false);
            try (PreparedStatement ps = retenedora.prepareStatement("SELECT id FROM citas WHERE id = ? FOR UPDATE")) {
                ps.setObject(1, cita);
                ps.executeQuery().close();
            }

            long inicio = System.nanoTime();
            MvcResult r = mockMvc.perform(patch("/citas/" + cita + "/cancelar").header("Authorization", recepcion.bearer())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Cancelacion con fila bloqueada\"}")).andReturn();
            long segundos = (System.nanoTime() - inicio) / 1_000_000_000L;

            assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(409);
            JsonNode cuerpo = leer(r);
            assertThat(cuerpo.get("message").asText()).isEqualTo("La cita está siendo modificada por otra operación, inténtelo de nuevo.");
            assertThat(segundos).as("espera maxima").isBetween(3L, 15L);
            assertThat(estadoDe(cita)).isEqualTo("PROGRAMADA");
            retenedora.rollback();
        }

        // la conexion de la peticion fallida volvio al pool y la cita se puede cancelar con normalidad
        MvcResult ok = mockMvc.perform(patch("/citas/" + cita + "/cancelar").header("Authorization", recepcion.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Cancelacion ya sin bloqueo\"}")).andReturn();
        assertThat(ok.getResponse().getStatus()).isEqualTo(200);
        assertThat(estadoDe(cita)).isEqualTo("CANCELADA");
    }
}
