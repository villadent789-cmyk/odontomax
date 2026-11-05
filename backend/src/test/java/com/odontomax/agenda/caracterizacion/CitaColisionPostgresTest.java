package com.odontomax.agenda.caracterizacion;

import com.fasterxml.jackson.databind.JsonNode;
import com.odontomax.agenda.application.port.out.CitaRepositoryPort;
import com.odontomax.agenda.domain.exception.CitaColisionException;
import com.odontomax.agenda.domain.model.Cita;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * B-030 y B-031 con pila completa: colisión por intervalo [hora, hora + duración) del mismo especialista o sillón,
 * ignorando CANCELADA, NO_ASISTIO y EXPIRADA (el mismo criterio que las restricciones EXCLUDE de la base), con un
 * 409 claro, y la restricción de la base traducida a {@link CitaColisionException}.
 */
@Testcontainers(disabledWithoutDocker = true)
class CitaColisionPostgresTest extends AgendaPostgresFixtures {

    @Autowired
    private CitaRepositoryPort citaRepository;

    private String sede;
    private UsuarioPrueba recepcion;
    private UUID paciente;
    private UUID doctor;
    private LocalDate fecha;

    @BeforeEach
    void preparar() {
        sede = "sede-col-" + tag();
        insertarSede(sede, "Colision " + sede, true, 3);
        recepcion = crearUsuario("RECEPCION", sede);
        paciente = crearPaciente(sede);
        doctor = crearEspecialista(null, sede);
        asignarSillon(doctor, sede, 1);
        fecha = fechaUnica();
    }

    private MvcResult agendar(UUID doctorId, Integer sillon, LocalTime hora, int duracion) throws Exception {
        String cuerpo = "{\"pacienteId\":\"" + paciente + "\",\"fecha\":\"" + fecha + "\",\"hora\":\"" + hora + "\","
                + "\"duracionMinutos\":" + duracion + ",\"motivoConsulta\":\"Control\""
                + (doctorId != null ? ",\"doctorId\":\"" + doctorId + "\"" : "")
                + (sillon != null ? ",\"sillonNumero\":" + sillon : "") + "}";
        return mockMvc.perform(post("/citas").header("Authorization", recepcion.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andReturn();
    }

    private String mensaje(MvcResult r) throws Exception {
        return leer(r).get("message").asText();
    }

    @Test
    @DisplayName("B-030: un solape parcial del mismo especialista responde 409 con mensaje de colisión de horario; las citas contiguas se aceptan")
    void solapeParcialDoctor() throws Exception {
        insertarCita(paciente, doctor, sede, null, fecha, LocalTime.of(10, 0), 60, "PROGRAMADA");

        for (LocalTime hora : new LocalTime[]{LocalTime.of(10, 0), LocalTime.of(10, 30), LocalTime.of(9, 45)}) {
            MvcResult r = agendar(doctor, null, hora, 30);
            assertThat(r.getResponse().getStatus()).as("hora " + hora).isEqualTo(409);
            assertThat(mensaje(r)).as("hora " + hora).contains("El doctor ya tiene una cita agendada");
        }
        assertThat(agendar(doctor, null, LocalTime.of(11, 0), 30).getResponse().getStatus()).isEqualTo(201);
        assertThat(agendar(doctor, null, LocalTime.of(9, 30), 30).getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    @DisplayName("B-030: un solape parcial del mismo sillón responde 409 con mensaje de sillón ocupado; otro sillón u otra sede no chocan")
    void solapeParcialSillon() throws Exception {
        insertarCita(paciente, null, sede, 1, fecha, LocalTime.of(10, 0), 45, "CONFIRMADA");

        MvcResult r = agendar(null, 1, LocalTime.of(10, 30), 30);
        assertThat(r.getResponse().getStatus()).isEqualTo(409);
        assertThat(mensaje(r)).contains("sillón #1").contains("ocupado");

        assertThat(agendar(null, 2, LocalTime.of(10, 30), 30).getResponse().getStatus()).isEqualTo(201);
        assertThat(agendar(null, 1, LocalTime.of(10, 45), 30).getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    @DisplayName("B-031: CANCELADA, NO_ASISTIO y EXPIRADA liberan el hueco del especialista y del sillón (como la restricción de la base)")
    void estadosQueLiberanElHueco() throws Exception {
        insertarCita(paciente, doctor, sede, 1, fecha, LocalTime.of(9, 0), 30, "CANCELADA");
        insertarCita(paciente, doctor, sede, 1, fecha, LocalTime.of(10, 0), 30, "NO_ASISTIO");
        insertarCita(paciente, doctor, sede, 1, fecha, LocalTime.of(11, 0), 30, "EXPIRADA");

        for (LocalTime hora : new LocalTime[]{LocalTime.of(9, 0), LocalTime.of(10, 0), LocalTime.of(11, 0)}) {
            MvcResult r = agendar(doctor, 1, hora, 30);
            assertThat(r.getResponse().getStatus()).as("hora " + hora + ": " + r.getResponse().getContentAsString()).isEqualTo(201);
        }
    }

    @Test
    @DisplayName("Reprogramar a un horario que se cruza con otra cita del especialista responde 409 con el mensaje de colisión; reprogramar sobre sí misma no choca")
    void reprogramarColision() throws Exception {
        insertarCita(paciente, doctor, sede, null, fecha, LocalTime.of(10, 0), 60, "PROGRAMADA");
        UUID movil = insertarCita(paciente, doctor, sede, null, fecha, LocalTime.of(15, 0), 30, "PROGRAMADA");

        String cuerpo = "{\"nuevaFecha\":\"" + fecha + "\",\"nuevoHorario\":\"10:30\",\"motivo\":\"Cambio pedido por el paciente\"}";
        MvcResult r = mockMvc.perform(patch("/citas/" + movil + "/reprogramar").header("Authorization", recepcion.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(409);
        assertThat(mensaje(r)).contains("El doctor ya tiene una cita agendada");

        String propio = "{\"nuevaFecha\":\"" + fecha + "\",\"nuevoHorario\":\"15:10\",\"motivo\":\"Pequeño ajuste de hora\"}";
        assertThat(mockMvc.perform(patch("/citas/" + movil + "/reprogramar").header("Authorization", recepcion.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(propio)).andReturn().getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("La restricción EXCLUDE de la base sigue siendo la red de seguridad: si salta (carrera), se traduce a CitaColisionException (409), no al error genérico de integridad")
    void restriccionDeLaBaseSeTraduce() {
        insertarCita(paciente, doctor, sede, 2, fecha, LocalTime.of(10, 0), 60, "PROGRAMADA");

        Cita choqueDoctor = Cita.builder().id(UUID.randomUUID()).pacienteId(paciente).doctorId(doctor).sedeId(sede)
                .motivoConsulta("Control").fecha(fecha).hora(LocalTime.of(10, 30)).duracionMinutos(30).estado("PROGRAMADA").build();
        assertThatThrownBy(() -> citaRepository.save(choqueDoctor))
                .isInstanceOf(CitaColisionException.class)
                .hasMessageContaining("especialista");

        Cita choqueSillon = Cita.builder().id(UUID.randomUUID()).pacienteId(paciente).doctorId(null).sedeId(sede).sillonNumero(2)
                .motivoConsulta("Control").fecha(fecha).hora(LocalTime.of(10, 30)).duracionMinutos(30).estado("PROGRAMADA").build();
        assertThatThrownBy(() -> citaRepository.save(choqueSillon))
                .isInstanceOf(CitaColisionException.class)
                .hasMessageContaining("sillón");
    }

    @Test
    @DisplayName("B-031: la disponibilidad trata los huecos de NO_ASISTIO y EXPIRADA como libres y marca ocupada la franja que se cruza con una cita que no empieza en punto")
    void disponibilidadCoherenteConLaColision() throws Exception {
        int dia = fecha.getDayOfWeek().getValue();
        jdbc.update("INSERT INTO especialista_horarios (especialista_id, sede_id, dia_semana, hora_inicio, hora_fin, activo) "
                + "VALUES (?, ?, ?, '09:00', '12:00', true)", doctor, sede, dia);
        insertarCita(paciente, doctor, sede, null, fecha, LocalTime.of(9, 0), 30, "NO_ASISTIO");
        insertarCita(paciente, doctor, sede, null, fecha, LocalTime.of(9, 30), 30, "EXPIRADA");
        insertarCita(paciente, doctor, sede, null, fecha, LocalTime.of(10, 15), 30, "PROGRAMADA");

        MvcResult r = mockMvc.perform(get("/especialistas/" + doctor + "/disponibilidad")
                .param("fecha", fecha.toString()).param("sedeId", sede).header("Authorization", recepcion.bearer())).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        JsonNode slots = leer(r).get("slots");
        assertThat(slot(slots, "09:00")).isTrue();
        assertThat(slot(slots, "09:30")).isTrue();
        assertThat(slot(slots, "10:00")).as("10:00-10:30 se cruza con 10:15-10:45").isFalse();
        assertThat(slot(slots, "10:30")).as("10:30-11:00 se cruza con 10:15-10:45").isFalse();
        assertThat(slot(slots, "11:00")).isTrue();
    }

    private static boolean slot(JsonNode slots, String hora) {
        for (JsonNode s : slots) {
            if (s.get("hora").asText().startsWith(hora)) {
                return s.get("disponible").asBoolean();
            }
        }
        throw new AssertionError("no hay franja " + hora);
    }
}
