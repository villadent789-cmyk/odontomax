package com.odontomax.pacientes.caracterizacion;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Pacientes con hallazgos por tratar y sin plan ni presupuesto: aparecen, y dejan de aparecer cuando se les arma uno. */
@Testcontainers(disabledWithoutDocker = true)
class PacientesSinPlanPostgresTest extends PacientesPostgresTestBase {

    private static final String SEDE = "sede-chilca-1";

    private void marcar(UsuarioPrueba admin, UUID paciente, int pieza, String estado) throws Exception {
        MvcResult r = ejecutar(post("/odontograma/pacientes/" + paciente + "/pieza"), admin, Map.of("piezaFdi", pieza, "estado", estado, "caras", List.of()));
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(201);
    }

    private JsonNode sinPlan(UsuarioPrueba admin) throws Exception {
        MvcResult r = ejecutar(get("/tratamientos/pacientes-sin-plan").param("sedeId", SEDE), admin);
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(200);
        return leer(r);
    }

    private JsonNode fila(JsonNode lista, UUID paciente) {
        for (JsonNode n : lista) {
            if (n.get("pacienteId").asText().equals(paciente.toString())) return n;
        }
        return null;
    }

    @Test
    @DisplayName("Lista a quien tiene hallazgos por tratar y sin plan; deja de listar al hacerle un presupuesto; ignora lo ya tratado y lo sano")
    void listaYSeVacia() throws Exception {
        UsuarioPrueba admin = adminGeneral();
        UUID conCaries = insertarPaciente(SEDE, "Caries", "Pendiente " + tag());
        UUID soloRealizado = insertarPaciente(SEDE, "Solo", "Tratado " + tag());
        UUID limpiado = insertarPaciente(SEDE, "Pieza", "Limpiada " + tag());

        marcar(admin, conCaries, 16, "caries");
        marcar(admin, conCaries, 16, "fractura");
        marcar(admin, conCaries, 26, "caries");
        marcar(admin, soloRealizado, 11, "restauracion");   // en buen estado: no es por tratar
        marcar(admin, limpiado, 36, "caries");
        marcar(admin, limpiado, 36, "sano");                // quedó sana

        JsonNode lista = sinPlan(admin);
        JsonNode f = fila(lista, conCaries);
        assertThat(f).isNotNull();
        assertThat(f.get("hallazgos").asInt()).isEqualTo(3);
        assertThat(List.of(f.get("tipos").get(0).asText(), f.get("tipos").get(1).asText())).containsExactlyInAnyOrder("caries", "fractura");
        assertThat(fila(lista, soloRealizado)).isNull();
        assertThat(fila(lista, limpiado)).isNull();

        // Un presupuesto vigente lo saca de la lista.
        MvcResult esp = ejecutar(get("/especialistas"), admin);
        UUID doctor = UUID.fromString(leer(esp).get(0).get("id").asText());
        MvcResult creado = ejecutar(post("/presupuestos"), admin, Map.of("pacienteId", conCaries.toString(), "sedeId", SEDE, "especialistaId", doctor.toString(),
                "items", List.of(Map.of("nombre", "Curación", "piezaFdi", 16, "cantidad", 1, "precioUnitario", 80))));
        assertThat(creado.getResponse().getStatus()).isEqualTo(201);
        assertThat(fila(sinPlan(admin), conCaries)).isNull();
    }
}
