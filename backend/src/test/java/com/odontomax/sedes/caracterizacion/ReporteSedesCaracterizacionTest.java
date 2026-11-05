package com.odontomax.sedes.caracterizacion;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Lo que {@code ReporteService} lee de sedes (plan de sedes, sección 8.4): ids de las sedes activas por nombre y
 * nombre de una sede por id (cualquier estado; el propio id si no existe). Protege el paso P6.
 */
@Testcontainers(disabledWithoutDocker = true)
class ReporteSedesCaracterizacionTest extends SedesPostgresTestBase {

    private JsonNode financiero(UsuarioPrueba u, String sedeId) throws Exception {
        var b = get("/reportes/financiero").header("Authorization", u.bearer());
        if (sedeId != null) {
            b = b.param("sedeId", sedeId);
        }
        MvcResult r = mockMvc.perform(b).andReturn();
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(200);
        return leer(r);
    }

    @Test
    @DisplayName("ADMIN_GENERAL sin sedeId: 'Todas las Sedes' y una entrada por sede activa, por nombre, con su nombre real")
    void todasLasSedes() throws Exception {
        String t = tag();
        insertarSede("sede-rep-act-" + t, "AAA Reporte " + t, true, 1);
        insertarSede("sede-rep-inact-" + t, "AAA Reporte Inactiva " + t, false, 1);

        JsonNode rep = financiero(adminGeneral(), null);
        assertThat(rep.get("sedeId").isNull()).isTrue();
        assertThat(rep.get("sedeNombre").asText()).isEqualTo("Todas las Sedes");

        List<Map<String, Object>> activas = jdbc.queryForList("SELECT id, nombre FROM sedes WHERE activa ORDER BY nombre");
        List<String> ids = new ArrayList<>();
        List<String> nombres = new ArrayList<>();
        rep.get("ingresosPorSede").forEach(n -> {
            ids.add(n.get("sedeId").asText());
            nombres.add(n.get("sedeNombre").asText());
        });
        assertThat(ids).containsExactlyElementsOf(activas.stream().map(m -> (String) m.get("id")).toList())
                .contains("sede-rep-act-" + t).doesNotContain("sede-rep-inact-" + t);
        assertThat(nombres).containsExactlyElementsOf(activas.stream().map(m -> (String) m.get("nombre")).toList());
    }

    @Test
    @DisplayName("ADMIN_GENERAL con sedeId: nombre real (también de una sede inactiva); id inexistente → el propio id")
    void unaSede() throws Exception {
        UsuarioPrueba admin = adminGeneral();
        JsonNode rep = financiero(admin, SEDE_ELTAMBO);
        assertThat(rep.get("sedeId").asText()).isEqualTo(SEDE_ELTAMBO);
        assertThat(rep.get("sedeNombre").asText()).isEqualTo("Villadent Huancayo Centro");
        assertThat(rep.get("ingresosPorSede")).hasSize(1);
        assertThat(rep.get("ingresosPorSede").get(0).get("sedeNombre").asText()).isEqualTo("Villadent Huancayo Centro");

        String inactiva = "sede-rep-inact-" + tag();
        insertarSede(inactiva, "Reporte Inactiva " + inactiva, false, 1);
        assertThat(financiero(admin, inactiva).get("sedeNombre").asText()).isEqualTo("Reporte Inactiva " + inactiva);

        String inexistente = "sede-no-existe-" + tag();
        JsonNode rep3 = financiero(admin, inexistente);
        assertThat(rep3.get("sedeId").asText()).isEqualTo(inexistente);
        assertThat(rep3.get("sedeNombre").asText()).isEqualTo(inexistente);
        assertThat(rep3.get("ingresosPorSede").get(0).get("sedeNombre").asText()).isEqualTo(inexistente);
    }

    @Test
    @DisplayName("ADMIN_SEDE sin sedeId: su propia sede con su nombre real")
    void adminSede() throws Exception {
        JsonNode rep = financiero(crearUsuario("ADMIN_SEDE", SEDE_CHILCA_1), null);
        assertThat(rep.get("sedeId").asText()).isEqualTo(SEDE_CHILCA_1);
        assertThat(rep.get("sedeNombre").asText()).isEqualTo("Villadent Chilca");
    }
}
