package com.odontomax.sedes.caracterizacion;

import com.fasterxml.jackson.databind.JsonNode;
import com.odontomax.sedes.infrastructure.persistence.entity.SedeEntity;
import com.odontomax.sedes.infrastructure.persistence.entity.ServicioEntity;
import com.odontomax.sedes.infrastructure.persistence.repository.SpringDataSedeRepository;
import com.odontomax.sedes.infrastructure.persistence.repository.SpringDataServicioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Observaciones O-3, O-4 y O-5 del plan de sedes (sección 10). O-3 y O-5 fijadas tal como se comportan hoy; O-4
 * (B-023) corregida en la Fase 3.5.
 */
@Testcontainers(disabledWithoutDocker = true)
class SedesObservacionesCaracterizacionTest extends SedesPostgresTestBase {

    @Autowired
    private SpringDataSedeRepository sedeRepository;

    @Autowired
    private SpringDataServicioRepository servicioRepository;

    private String crearSedeHttp(UsuarioPrueba admin, String nombre, int sillones) throws Exception {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("nombre", nombre);
        cuerpo.put("direccion", "Dir " + nombre);
        cuerpo.put("sillones", sillones);
        MvcResult r = mockMvc.perform(post("/sedes").header("Authorization", admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(json(cuerpo))).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(201);
        return leer(r).get("id").asText();
    }

    private UUID insertarPaciente(String sedeId) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO pacientes (id, dni, nombres, apellidos, sede_id) VALUES (?, ?, ?, ?, ?)",
                id, "D" + id.toString().substring(0, 8), "Paciente", "Observacion", sedeId);
        return id;
    }

    private MvcResult agendar(UsuarioPrueba u, String sedeId, UUID pacienteId, int sillon) throws Exception {
        Map<String, Object> cita = new HashMap<>();
        cita.put("pacienteId", pacienteId);
        cita.put("pacienteNombre", "Paciente Observacion");
        cita.put("sedeId", sedeId);
        cita.put("fecha", LocalDate.now().plusDays(30).toString());
        cita.put("hora", LocalTime.of(10, 0).toString());
        cita.put("motivoConsulta", "Control");
        cita.put("sillonNumero", sillon);
        return mockMvc.perform(post("/citas").header("Authorization", u.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(json(cita))).andReturn();
    }

    @Test
    @DisplayName("O-3: save() con un id ya existente sobrescribe la fila sin error (sedes y servicios); por eso el alta concurrente puede pisar otra")
    void saveConIdExistenteSobrescribe() throws Exception {
        UsuarioPrueba admin = adminGeneral();
        String t = tag();
        String idSede = crearSedeHttp(admin, "Sede Original " + t, 3);

        sedeRepository.save(SedeEntity.builder().id(idSede).nombre("Sede Pisada " + t).direccion("Otra")
                .ciudad("Huancayo").sillones(1).build());
        Map<String, Object> fila = jdbc.queryForMap("SELECT nombre, direccion, sillones FROM sedes WHERE id = ?", idSede);
        assertThat(fila.get("nombre")).isEqualTo("Sede Pisada " + t);
        assertThat(((Number) fila.get("sillones")).intValue()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sedes WHERE id = ?", Integer.class, idSede)).isEqualTo(1);

        String idSrv = "srv-obs-" + t;
        servicioRepository.save(ServicioEntity.builder().id(idSrv).nombre("Original").especialidad("E").duracionMin(30).build());
        servicioRepository.save(ServicioEntity.builder().id(idSrv).nombre("Pisado").especialidad("E").duracionMin(60).build());
        assertThat(jdbc.queryForObject("SELECT nombre FROM servicios_web WHERE id = ?", String.class, idSrv)).isEqualTo("Pisado");
    }

    @Test
    @DisplayName("O-4 / B-023: POST /citas rechaza (400) un sillón fuera de 1..sillones de la sede y una sede inactiva; nada se guarda")
    void sillonYSedeInactivaSeValidan() throws Exception {
        UsuarioPrueba admin = adminGeneral();
        String sede = crearSedeHttp(admin, "Sede Dos Sillones " + tag(), 2);
        UUID paciente = insertarPaciente(sede);
        for (int sillon : new int[]{9, 3, 0, -1}) {
            MvcResult r = agendar(admin, sede, paciente, sillon);
            assertThat(r.getResponse().getStatus()).as("sillón %s: %s", sillon, r.getResponse().getContentAsString())
                    .isEqualTo(400);
            JsonNode b = leer(r);
            assertThat(b.get("error").asText()).isEqualTo("Bad Request");
            assertThat(b.get("message").asText()).isEqualTo("El sillón #" + sillon + " no existe en la sede '" + sede
                    + "' (sillones del 1 al 2).");
            assertThat(nombresDeCampos(b)).containsExactlyInAnyOrder("timestamp", "status", "error", "message", "mensaje");
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM citas WHERE sede_id = ?", Integer.class, sede)).isZero();

        MvcResult r = agendar(admin, sede, paciente, 2);
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(201);
        assertThat(leer(r).get("sillonNumero").asInt()).isEqualTo(2);
        assertThat(leer(r).get("sedeId").asText()).isEqualTo(sede);

        String inactiva = "sede-obs-inact-" + tag();
        insertarSede(inactiva, "Obs Inactiva " + inactiva, false, 1);
        UUID pacienteInactiva = insertarPaciente(inactiva);
        r = agendar(admin, inactiva, pacienteInactiva, 1);
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(400);
        assertThat(leer(r).get("message").asText())
                .isEqualTo("La sede '" + inactiva + "' está inactiva y no admite nuevas citas.");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM citas WHERE sede_id = ?", Integer.class, inactiva)).isZero();
    }

    @Test
    @DisplayName("O-5: sede_horarios solo tiene la semilla (6 días por sede); las sedes nuevas no reciben filas y la API no la expone")
    void sedeHorariosSinUso() throws Exception {
        for (String semilla : new String[]{SEDE_CHILCA_1, SEDE_ELTAMBO}) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM sede_horarios WHERE sede_id = ?", Integer.class, semilla))
                    .as(semilla).isEqualTo(6);
        }
        UsuarioPrueba admin = adminGeneral();
        String nueva = crearSedeHttp(admin, "Sede Sin Horarios " + tag(), 1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sede_horarios WHERE sede_id = ?", Integer.class, nueva)).isZero();

        // El único horario visible es el texto libre horario_texto
        JsonNode chilca = buscarPorId(leer(mockMvc.perform(get("/sedes")).andReturn()), SEDE_CHILCA_1);
        assertThat(chilca.get("horarioTexto").asText()).isEqualTo("Lun - Sáb: 9:00 a.m. - 8:00 p.m.");
        assertThat(chilca.has("horarios")).isFalse();
    }
}
