package com.odontomax.pacientes.caracterizacion;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Una pieza puede tener varios hallazgos a la vez; quitar uno no toca los demás y «sano» limpia la pieza entera. */
@Testcontainers(disabledWithoutDocker = true)
class OdontogramaHallazgosPostgresTest extends PacientesPostgresTestBase {

    private static final String SEDE = "sede-chilca-1";

    private void registrar(UsuarioPrueba u, UUID paciente, int pieza, String estado, List<String> caras, boolean activo) throws Exception {
        var r = ejecutar(post("/odontograma/pacientes/" + paciente + "/pieza"), u,
                Map.of("piezaFdi", pieza, "estado", estado, "caras", caras, "activo", activo));
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(201);
    }

    private JsonNode actual(UsuarioPrueba u, UUID paciente) throws Exception {
        return leer(ejecutar(get("/odontograma/pacientes/" + paciente), u));
    }

    @Test
    @DisplayName("Varios hallazgos en la misma pieza: se suman, se quitan por separado y «sano» limpia la pieza")
    void variosHallazgosPorPieza() throws Exception {
        UsuarioPrueba admin = adminGeneral();
        UUID paciente = insertarPaciente(SEDE, "Rosa", "Quispe " + tag());

        registrar(admin, paciente, 16, "caries", List.of("O"), true);
        registrar(admin, paciente, 16, "corona_mal_estado", List.of(), true);
        registrar(admin, paciente, 26, "restauracion", List.of("M"), true);
        assertThat(actual(admin, paciente)).hasSize(3);

        // Cambiar las caras de un hallazgo reemplaza solo ese hallazgo.
        registrar(admin, paciente, 16, "caries", List.of("O", "D"), true);
        JsonNode tras = actual(admin, paciente);
        assertThat(tras).hasSize(3);
        for (JsonNode h : tras) {
            if (h.get("piezaFdi").asInt() == 16 && h.get("estado").asText().equals("caries")) {
                assertThat(h.get("caras")).hasSize(2);
            }
        }

        // Quitar la caries deja la corona.
        registrar(admin, paciente, 16, "caries", List.of(), false);
        JsonNode sinCaries = actual(admin, paciente);
        assertThat(sinCaries).hasSize(2);
        assertThat(sinCaries.findValuesAsText("estado")).containsExactlyInAnyOrder("corona_mal_estado", "restauracion");

        // «Sano» limpia toda la pieza 16 y no toca la 26; la 16 queda marcada como sana.
        registrar(admin, paciente, 16, "sano", List.of(), true);
        JsonNode limpia = actual(admin, paciente);
        assertThat(limpia).hasSize(2);
        assertThat(limpia.get(0).get("piezaFdi").asInt()).isEqualTo(16);
        assertThat(limpia.get(0).get("estado").asText()).isEqualTo("sano");
        assertThat(limpia.get(1).get("piezaFdi").asInt()).isEqualTo(26);

        // Un hallazgo nuevo después de limpiar sí cuenta.
        registrar(admin, paciente, 16, "fractura", List.of(), true);
        JsonNode conFractura = actual(admin, paciente);
        assertThat(conFractura).hasSize(2);
        assertThat(conFractura.findValuesAsText("estado")).containsExactlyInAnyOrder("fractura", "restauracion");
    }

    @Test
    @DisplayName("El estado actual de pacientes con registros anteriores no cambia: manda el último de cada pieza")
    void registrosAnterioresConservanSuEstado() throws Exception {
        UsuarioPrueba admin = adminGeneral();
        UUID paciente = insertarPaciente(SEDE, "Luis", "Meza " + tag());
        insertarOdontograma(paciente, 16);

        assertThat(actual(admin, paciente)).hasSize(1);
    }
}
