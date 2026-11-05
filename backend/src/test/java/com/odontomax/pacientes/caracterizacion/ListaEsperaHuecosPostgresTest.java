package com.odontomax.pacientes.caracterizacion;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Lista de espera de huecos: recepción apunta a quien espera, y cuando una cita se cancela y deja un hueco libre la
 * bandeja sugiere ofrecérselo con el mensaje de WhatsApp ya escrito. Las citas también traen las inasistencias.
 */
@Testcontainers(disabledWithoutDocker = true)
class ListaEsperaHuecosPostgresTest extends PacientesPostgresTestBase {

    private static final String SEDE = "sede-chilca-1";

    private LocalDate manana() {
        return LocalDate.now(ZoneId.of("America/Lima")).plusDays(1);
    }

    private String agregar(UsuarioPrueba recepcion, UUID paciente, String motivo) throws Exception {
        MvcResult r = ejecutar(post("/citas/espera-hueco"), recepcion,
                Map.of("sedeId", SEDE, "pacienteId", paciente.toString(), "motivo", motivo));
        assertThat(r.getResponse().getStatus()).isEqualTo(201);
        return leer(r).get("id").asText();
    }

    @Test
    @DisplayName("Recepción agrega a un paciente a la lista de espera, no se repite y el odontólogo no puede")
    void agregarYListar() throws Exception {
        UsuarioPrueba recepcion = crearUsuario("RECEPCION", SEDE);
        UUID paciente = insertarPaciente(SEDE, "Luciana", "Meza " + tag());

        String id = agregar(recepcion, paciente, "Limpieza dental");

        assertThat(ids(leer(ejecutar(get("/citas/espera-hueco").param("sedeId", SEDE), recepcion)))).contains(id);
        MvcResult repetido = ejecutar(post("/citas/espera-hueco"), recepcion,
                Map.of("sedeId", SEDE, "pacienteId", paciente.toString(), "motivo", "Otra vez"));
        assertThat(repetido.getResponse().getStatus()).isEqualTo(400);

        UsuarioPrueba odontologo = crearUsuario("ODONTOLOGO", SEDE);
        assertThat(ejecutar(post("/citas/espera-hueco"), odontologo,
                Map.of("sedeId", SEDE, "pacienteId", paciente.toString(), "motivo", "Limpieza")).getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("Una cita cancelada de mañana deja un hueco: la bandeja lo ofrece al primero de la lista de espera")
    void huecoSugeridoEnLaBandeja() throws Exception {
        UsuarioPrueba recepcion = crearUsuario("RECEPCION", SEDE);
        UUID quienCancela = insertarPaciente(SEDE, "Carlos", "Soto " + tag());
        UUID quienEspera = insertarPaciente(SEDE, "Rosa", "Mendoza " + tag());
        insertarCita(quienCancela, SEDE, "CANCELADA", manana());
        agregar(recepcion, quienEspera, "Limpieza dental");

        JsonNode tareas = leer(ejecutar(get("/citas/bandeja-tareas").param("sedeId", SEDE), recepcion)).get("tareas");

        JsonNode hueco = null;
        for (JsonNode t : tareas) {
            if ("HUECO_DISPONIBLE".equals(t.get("tipo").asText())) hueco = t;
        }
        assertThat(hueco).isNotNull();
        assertThat(hueco.get("pacienteId").asText()).isEqualTo(quienEspera.toString());
        assertThat(hueco.get("mensajeSugerido").asText()).contains("mañana").contains("Limpieza dental");
    }

    @Test
    @DisplayName("Si otra cita ya ocupa ese horario no hay hueco que ofrecer")
    void sinHuecoSiOtraCitaLoOcupa() throws Exception {
        UsuarioPrueba recepcion = crearUsuario("RECEPCION", SEDE);
        UUID quienCancela = insertarPaciente(SEDE, "Carlos", "Soto " + tag());
        UUID otro = insertarPaciente(SEDE, "Ana", "Lopez " + tag());
        UUID quienEspera = insertarPaciente(SEDE, "Rosa", "Mendoza " + tag());
        insertarCita(quienCancela, SEDE, "CANCELADA", manana());
        insertarCita(otro, SEDE, "PROGRAMADA", manana());
        agregar(recepcion, quienEspera, "Limpieza dental");

        JsonNode tareas = leer(ejecutar(get("/citas/bandeja-tareas").param("sedeId", SEDE), recepcion)).get("tareas");

        for (JsonNode t : tareas) {
            assertThat(t.get("tipo").asText()).isNotEqualTo("HUECO_DISPONIBLE");
        }
    }

    @Test
    @DisplayName("Ofrecido y agendado: la espera sale de la bandeja; una espera agendada no se reabre")
    void cambiarEstado() throws Exception {
        UsuarioPrueba recepcion = crearUsuario("RECEPCION", SEDE);
        UUID paciente = insertarPaciente(SEDE, "Rosa", "Mendoza " + tag());
        String id = agregar(recepcion, paciente, "Limpieza dental");

        assertThat(ejecutar(patch("/citas/espera-hueco/" + id + "/estado"), recepcion, Map.of("estado", "AGENDADA")).getResponse().getStatus()).isEqualTo(200);

        assertThat(ids(leer(ejecutar(get("/citas/espera-hueco").param("sedeId", SEDE), recepcion)))).doesNotContain(id);
        assertThat(ejecutar(patch("/citas/espera-hueco/" + id + "/estado"), recepcion, Map.of("estado", "ACTIVA")).getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("La agenda trae las inasistencias de cada paciente para marcar a quien falta seguido")
    void citasConInasistencias() throws Exception {
        UsuarioPrueba recepcion = crearUsuario("RECEPCION", SEDE);
        UUID paciente = insertarPaciente(SEDE, "Carlos", "Soto " + tag());
        jdbc.update("UPDATE pacientes SET total_inasistencias = 3 WHERE id = ?", paciente);
        UUID cita = insertarCita(paciente, SEDE, "PROGRAMADA", manana());

        JsonNode citas = leer(ejecutar(get("/citas").param("fecha", manana().toString()).param("sedeId", SEDE), recepcion));

        JsonNode fila = buscarPorId(citas, cita.toString());
        assertThat(fila).isNotNull();
        assertThat(fila.get("pacienteInasistencias").asInt()).isEqualTo(3);
    }
}
