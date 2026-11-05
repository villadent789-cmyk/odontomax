package com.odontomax.pacientes.caracterizacion;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Quien recomienda a un paciente nuevo queda con un descuento pendiente que el doctor da una sola vez. */
@Testcontainers(disabledWithoutDocker = true)
class DescuentoRecomendacionPostgresTest extends PacientesPostgresTestBase {

    private static final String SEDE = "sede-chilca-1";

    @Test
    @DisplayName("Registrar un paciente recomendado deja pendiente el descuento de quien lo recomendó, y se aplica una sola vez")
    void descuentoPorRecomendacion() throws Exception {
        UsuarioPrueba admin = adminGeneral();
        UUID recomendador = insertarPaciente(SEDE, "Marta", "Vera " + tag());

        Map<String, Object> cuerpo = new HashMap<>(cuerpoPaciente(SEDE));
        cuerpo.put("fuenteCaptacion", "REFERIDO_PACIENTE");
        cuerpo.put("referidoPorPacienteId", recomendador.toString());
        MvcResult registrado = ejecutar(post("/pacientes"), admin, cuerpo);
        assertThat(registrado.getResponse().getStatus()).as(registrado.getResponse().getContentAsString()).isEqualTo(201);
        String nuevoId = leer(registrado).get("id").asText();

        JsonNode lista = leer(ejecutar(get("/pacientes/" + recomendador + "/descuentos-recomendacion"), admin));
        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).get("estado").asText()).isEqualTo("PENDIENTE");
        assertThat(lista.get(0).get("referidoId").asText()).isEqualTo(nuevoId);
        assertThat(lista.get(0).get("referidoNombre").asText()).isNotBlank();

        String descuentoId = lista.get(0).get("id").asText();
        MvcResult aplicado = ejecutar(post("/pacientes/" + recomendador + "/descuentos-recomendacion/" + descuentoId + "/aplicar"), admin, Map.of());
        assertThat(aplicado.getResponse().getStatus()).as(aplicado.getResponse().getContentAsString()).isEqualTo(200);
        assertThat(leer(aplicado).get("estado").asText()).isEqualTo("APLICADO");
        assertThat(ejecutar(post("/pacientes/" + recomendador + "/descuentos-recomendacion/" + descuentoId + "/aplicar"), admin, Map.of())
                .getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    @DisplayName("Un paciente que no llegó por recomendación no genera descuento")
    void sinRecomendacionNoHayDescuento() throws Exception {
        UsuarioPrueba admin = adminGeneral();
        UUID otro = insertarPaciente(SEDE, "Pedro", "Ruiz " + tag());

        ejecutar(post("/pacientes"), admin, cuerpoPaciente(SEDE));

        assertThat(leer(ejecutar(get("/pacientes/" + otro + "/descuentos-recomendacion"), admin))).isEmpty();
    }
}
