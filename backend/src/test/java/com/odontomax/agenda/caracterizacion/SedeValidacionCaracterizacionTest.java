package com.odontomax.agenda.caracterizacion;

import com.fasterxml.jackson.databind.JsonNode;
import com.odontomax.agenda.application.port.out.SedeValidacionPort;
import com.odontomax.sedes.caracterizacion.SedesPostgresTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Lo que agenda necesita de sedes (plan de sedes, sección 8.5): semántica de {@code SedeValidacionPort.existeSede}
 * (hoy {@code SedeCrossModuleAdapter} con SQL) y su efecto en {@code POST /especialistas}. Protege P1-P2.
 */
@Testcontainers(disabledWithoutDocker = true)
class SedeValidacionCaracterizacionTest extends SedesPostgresTestBase {

    @Autowired
    private SedeValidacionPort sedeValidacionPort;

    @Test
    @DisplayName("existeSede: null/blanco false; recorta espacios; inactiva true; distingue mayúsculas; inexistente false")
    void existeSede() {
        String inactiva = "sede-val-inact-" + tag();
        insertarSede(inactiva, "Validacion Inactiva " + inactiva, false, 1);

        assertThat(sedeValidacionPort.existeSede(null)).isFalse();
        assertThat(sedeValidacionPort.existeSede("")).isFalse();
        assertThat(sedeValidacionPort.existeSede("   ")).isFalse();
        assertThat(sedeValidacionPort.existeSede(SEDE_CHILCA_1)).isTrue();
        assertThat(sedeValidacionPort.existeSede("  " + SEDE_CHILCA_1 + "  ")).isTrue();
        assertThat(sedeValidacionPort.existeSede(inactiva)).isTrue();
        assertThat(sedeValidacionPort.existeSede("SEDE-CHILCA-1")).isFalse();
        assertThat(sedeValidacionPort.existeSede("sede-no-existe-" + tag())).isFalse();
    }

    private MvcResult crearEspecialista(UsuarioPrueba admin, List<String> sedes) throws Exception {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("nombre", "Dr. Validación " + tag());
        cuerpo.put("especialidad", "Ortodoncia");
        cuerpo.put("sedes", sedes);
        return mockMvc.perform(post("/especialistas").header("Authorization", admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(json(cuerpo))).andReturn();
    }

    @Test
    @DisplayName("POST /especialistas: sede inexistente o en otra capitalización 400 'La sede 'x' no existe.'; inactiva 400 (B-023); con espacios 201")
    void postEspecialistas() throws Exception {
        UsuarioPrueba admin = adminGeneral();
        String inexistente = "sede-no-existe-" + tag();

        MvcResult r = crearEspecialista(admin, List.of(SEDE_CHILCA_1, inexistente));
        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        JsonNode b = leer(r);
        assertThat(b.get("error").asText()).isEqualTo("Bad Request");
        assertThat(b.get("message").asText()).isEqualTo("La sede '" + inexistente + "' no existe.");

        r = crearEspecialista(admin, List.of("SEDE-CHILCA-1"));
        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        assertThat(leer(r).get("message").asText()).isEqualTo("La sede 'SEDE-CHILCA-1' no existe.");

        String inactiva = "sede-val-inact-" + tag();
        insertarSede(inactiva, "Validacion Inactiva " + inactiva, false, 1);
        r = crearEspecialista(admin, List.of(SEDE_CHILCA_1, inactiva));
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(400);
        assertThat(leer(r).get("error").asText()).isEqualTo("Bad Request");
        assertThat(leer(r).get("message").asText()).isEqualTo("La sede '" + inactiva + "' está inactiva.");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM especialista_sedes WHERE sede_id = ?", Integer.class, inactiva))
                .isZero();

        r = crearEspecialista(admin, List.of("  " + SEDE_ELTAMBO + "  "));
        assertThat(r.getResponse().getStatus()).isEqualTo(201);
        assertThat(leer(r).get("sedes").get(0).asText()).isEqualTo(SEDE_ELTAMBO);
    }

    @Test
    @DisplayName("PUT /especialistas/{id} (B-023): añadir una sede inactiva 400; una sede ya asignada que se desactiva se conserva (200)")
    void putEspecialistas() throws Exception {
        UsuarioPrueba admin = adminGeneral();
        String sede = "sede-val-put-" + tag();
        insertarSede(sede, "Validacion Put " + sede, true, 1);
        MvcResult r = crearEspecialista(admin, List.of(sede));
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(201);
        String id = leer(r).get("id").asText();
        jdbc.update("UPDATE sedes SET activa = false WHERE id = ?", sede);

        String inactiva = "sede-val-inact-" + tag();
        insertarSede(inactiva, "Validacion Inactiva " + inactiva, false, 1);
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("nombre", "Dr. Validación Put");
        cuerpo.put("especialidad", "Ortodoncia");
        cuerpo.put("sedes", List.of(sede, inactiva));
        r = mockMvc.perform(put("/especialistas/" + id).header("Authorization", admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(json(cuerpo))).andReturn();
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(400);
        assertThat(leer(r).get("message").asText()).isEqualTo("La sede '" + inactiva + "' está inactiva.");
        assertThat(jdbc.queryForObject("SELECT nombre FROM especialistas WHERE id = ?::uuid", String.class, id))
                .startsWith("Dr. Validación ");

        cuerpo.put("sedes", List.of(sede));
        r = mockMvc.perform(put("/especialistas/" + id).header("Authorization", admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(json(cuerpo))).andReturn();
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(200);
        assertThat(leer(r).get("sedes").get(0).asText()).isEqualTo(sede);
    }
}
