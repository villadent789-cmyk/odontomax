package com.odontomax.agenda.caracterizacion;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Creación de citas con pila completa: validación del motivo (B-029) y nombres rellenados por el servidor (B-032). */
@Testcontainers(disabledWithoutDocker = true)
class CitaCreacionPostgresTest extends AgendaPostgresFixtures {

    private String sede;
    private UsuarioPrueba recepcion;
    private UUID paciente;
    private LocalDate fecha;

    @BeforeEach
    void preparar() {
        sede = "sede-cre-" + tag();
        insertarSede(sede, "Creacion " + sede, true, 3);
        recepcion = crearUsuario("RECEPCION", sede);
        paciente = crearPaciente(sede);
        fecha = fechaUnica();
    }

    private MvcResult crear(String cuerpo) throws Exception {
        return mockMvc.perform(post("/citas").header("Authorization", recepcion.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andReturn();
    }

    private String cuerpo(String extra) {
        return "{\"pacienteId\":\"" + paciente + "\",\"fecha\":\"" + fecha + "\",\"hora\":\"09:00\"" + extra + "}";
    }

    @Test
    @DisplayName("B-029: sin motivoConsulta, con motivo en blanco o de más de 500 caracteres responde 400 de validación (antes 409 genérico) y no crea la cita")
    void motivoObligatorio() throws Exception {
        for (String extra : new String[]{"", ",\"motivoConsulta\":\"\"", ",\"motivoConsulta\":\"   \"",
                ",\"motivoConsulta\":\"" + "x".repeat(501) + "\""}) {
            MvcResult r = crear(cuerpo(extra));
            assertThat(r.getResponse().getStatus()).as(extra.length() > 60 ? "motivo largo" : extra).isEqualTo(400);
            JsonNode body = leer(r);
            assertThat(body.get("error").asText()).isEqualTo("Validación fallida");
            assertThat(body.get("detalles").has("motivoConsulta")).isTrue();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM citas WHERE paciente_id = ?", Integer.class, paciente)).isZero();
    }

    @Test
    @DisplayName("B-032: los nombres y el teléfono salen del paciente y del especialista reales; lo que envíe el cliente se ignora")
    void nombresDelServidor() throws Exception {
        UUID doctor = crearEspecialista(null, sede);
        asignarSillon(doctor, sede, 1);
        String nombreDoctor = jdbc.queryForObject("SELECT nombre FROM especialistas WHERE id = ?", String.class, doctor);
        String nombrePaciente = jdbc.queryForObject("SELECT trim(nombres || ' ' || apellidos) FROM pacientes WHERE id = ?", String.class, paciente);

        MvcResult r = crear(cuerpo(",\"motivoConsulta\":\"Control\",\"doctorId\":\"" + doctor + "\","
                + "\"pacienteNombre\":\"Nombre Falso\",\"pacienteTelefono\":\"000000000\",\"doctorNombre\":\"Dr. Falso\""));

        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(201);
        JsonNode body = leer(r);
        assertThat(body.get("pacienteNombre").asText()).isEqualTo(nombrePaciente);
        assertThat(body.get("pacienteTelefono").asText()).isEqualTo("987654321");
        assertThat(body.get("doctorNombre").asText()).isEqualTo(nombreDoctor);
        assertThat(body.get("sillonNumero").asInt()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT paciente_nombre || '|' || paciente_telefono || '|' || doctor_nombre FROM citas WHERE id = ?::uuid",
                String.class, body.get("id").asText())).isEqualTo(nombrePaciente + "|987654321|" + nombreDoctor);
    }

    @Test
    @DisplayName("Un doctor sin sillón fijo recibe 409 y no se crea la cita")
    void doctorSinSillonFijo() throws Exception {
        UUID doctor = crearEspecialista(null, sede);

        MvcResult r = crear(cuerpo(",\"motivoConsulta\":\"Control\",\"doctorId\":\"" + doctor + "\""));

        assertThat(r.getResponse().getStatus()).isEqualTo(409);
        assertThat(leer(r).get("message").asText())
                .isEqualTo("El doctor no tiene sillón asignado en esta sede; asígnalo en Administración");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM citas WHERE paciente_id = ?", Integer.class, paciente)).isZero();
    }

    @Test
    @DisplayName("B-032: sin nombres en la petición se rellenan igual, y un paciente o especialista inexistente responde 400 sin crear la cita")
    void inexistentes() throws Exception {
        MvcResult sinNombres = crear(cuerpo(",\"motivoConsulta\":\"Control\""));
        assertThat(sinNombres.getResponse().getStatus()).isEqualTo(201);
        assertThat(leer(sinNombres).get("pacienteNombre").asText()).startsWith("Paciente ");

        String fantasma = "{\"pacienteId\":\"" + UUID.randomUUID() + "\",\"fecha\":\"" + fecha + "\",\"hora\":\"11:00\",\"motivoConsulta\":\"Control\"}";
        MvcResult r = crear(fantasma);
        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        assertThat(leer(r).get("message").asText()).isEqualTo("El paciente indicado no existe.");

        r = crear(cuerpo(",\"motivoConsulta\":\"Control\",\"doctorId\":\"" + UUID.randomUUID() + "\""));
        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        assertThat(leer(r).get("message").asText()).isEqualTo("El especialista indicado no existe.");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM citas WHERE sede_id = ?", Integer.class, sede)).isEqualTo(1);
    }

    @Test
    @DisplayName("B-029: con motivoConsulta de hasta 500 caracteres la cita se crea (201)")
    void motivoValido() throws Exception {
        MvcResult r = crear(cuerpo(",\"motivoConsulta\":\"" + "x".repeat(500) + "\""));
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(201);
        assertThat(leer(r).get("motivoConsulta").asText()).hasSize(500);
    }
}
