package com.odontomax.pacientes.caracterizacion;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Plantillas de plan: se crean, se listan por negocio, no admiten nombres repetidos y se quitan sin borrar planes. */
@Testcontainers(disabledWithoutDocker = true)
class PlantillasPlanPostgresTest extends PacientesPostgresTestBase {

    private Map<String, Object> cuerpo(String nombre) {
        return Map.of("nombre", nombre, "procedimientoId", "srv-curacion", "servicioNombre", "Curación", "piezas", 3, "gravedad", "MODERADO", "sesiones", 2);
    }

    @Test
    @DisplayName("Crear, listar, rechazar el nombre repetido y quitar una plantilla")
    void ciclo() throws Exception {
        UsuarioPrueba admin = adminGeneral();
        String nombre = "Curación molares " + tag();

        MvcResult creada = ejecutar(post("/plantillas-plan"), admin, cuerpo(nombre));
        assertThat(creada.getResponse().getStatus()).as(creada.getResponse().getContentAsString()).isEqualTo(201);
        JsonNode j = leer(creada);
        assertThat(j.get("nombre").asText()).isEqualTo(nombre);
        assertThat(j.get("piezas").asInt()).isEqualTo(3);
        assertThat(j.get("gravedad").asText()).isEqualTo("MODERADO");
        String id = j.get("id").asText();

        assertThat(ids(leer(ejecutar(get("/plantillas-plan"), admin)))).contains(id);

        // El mismo nombre (sin importar mayúsculas) en el mismo negocio es un conflicto.
        assertThat(ejecutar(post("/plantillas-plan"), admin, cuerpo(nombre.toUpperCase())).getResponse().getStatus()).isEqualTo(409);

        assertThat(ejecutar(delete("/plantillas-plan/" + id), admin).getResponse().getStatus()).isEqualTo(204);
        assertThat(ids(leer(ejecutar(get("/plantillas-plan"), admin)))).doesNotContain(id);
        assertThat(ejecutar(delete("/plantillas-plan/" + id), admin).getResponse().getStatus()).isEqualTo(204);

        // Con la plantilla quitada se puede volver a usar el nombre.
        assertThat(ejecutar(post("/plantillas-plan"), admin, cuerpo(nombre)).getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    @DisplayName("Los datos inválidos se rechazan y el doctor puede crear pero no quitar")
    void validacionYRoles() throws Exception {
        UsuarioPrueba admin = adminGeneral();
        assertThat(ejecutar(post("/plantillas-plan"), admin, Map.of("nombre", "", "procedimientoId", "x", "servicioNombre", "X", "piezas", 1, "sesiones", 1)).getResponse().getStatus()).isEqualTo(400);
        assertThat(ejecutar(post("/plantillas-plan"), admin, Map.of("nombre", "Pieza cero " + tag(), "procedimientoId", "x", "servicioNombre", "X", "piezas", 0, "sesiones", 1)).getResponse().getStatus()).isEqualTo(400);

        UsuarioPrueba doctor = crearUsuario("ODONTOLOGO", "sede-chilca-1");
        MvcResult creada = ejecutar(post("/plantillas-plan"), doctor, cuerpo("Del doctor " + tag()));
        assertThat(creada.getResponse().getStatus()).isEqualTo(201);
        String id = leer(creada).get("id").asText();
        assertThat(ejecutar(delete("/plantillas-plan/" + id), doctor).getResponse().getStatus()).isEqualTo(403);
    }
}
