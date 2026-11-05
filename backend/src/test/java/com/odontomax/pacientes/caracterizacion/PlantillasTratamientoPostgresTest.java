package com.odontomax.pacientes.caracterizacion;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** Plantillas clínicas por tratamiento: catálogo de plantillas, ficha en borrador, cierre inmutable y corrección. */
@Testcontainers(disabledWithoutDocker = true)
class PlantillasTratamientoPostgresTest extends PacientesPostgresTestBase {

    @Test
    @DisplayName("Hay 14 plantillas (12 de Villadent y 2 de Villamax) y cada servicio del tarifario apunta a la suya")
    void catalogoDePlantillas() throws Exception {
        UsuarioPrueba admin = adminGeneral();

        assertThat(leer(ejecutar(get("/plantillas"), admin))).hasSize(14);
        assertThat(leer(ejecutar(get("/plantillas").param("negocio", "VILLAMAX"), admin))).hasSize(2);
        assertThat(leer(ejecutar(get("/plantillas/servicio/tar-endo-mono"), admin)).get("codigo").asText()).isEqualTo("ENDODONCIA");
        assertThat(leer(ejecutar(get("/plantillas/servicio/tar-liss-simple"), admin)).get("codigo").asText()).isEqualTo("RESTAURACION");
        assertThat(leer(ejecutar(get("/plantillas/servicio/vmx-tomografia-3d"), admin)).get("codigo").asText()).isEqualTo("TOMOGRAFIA_3D");
        assertThat(ejecutar(get("/plantillas/servicio/no-existe"), admin).getResponse().getStatus()).isEqualTo(404);
        // La firma se recoge en el consentimiento, no en las fichas de tratamiento.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM plantillas_tratamiento WHERE esquema::text ILIKE '%firma%'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM servicios_web s WHERE s.id LIKE 'tar-%' AND NOT EXISTS (SELECT 1 FROM servicio_plantilla sp WHERE sp.servicio_id = s.id)", Integer.class)).isZero();
    }

    @Test
    @DisplayName("La ficha nace en borrador, no cierra sin los datos obligatorios, y cerrada ya no se edita; se corrige con una ficha nueva")
    void fichaBorradorCierreYCorreccion() throws Exception {
        String sede = crearSede();
        UsuarioPrueba odontologo = crearUsuario("ODONTOLOGO", sede);
        UUID paciente = insertarPaciente(sede, "Mario", "Lazo " + tag());
        vincularCita(paciente, odontologo);

        MvcResult creada = ejecutar(post("/plantillas/registros").contentType(MediaType.APPLICATION_JSON), odontologo,
                Map.of("pacienteId", paciente.toString(), "plantillaCodigo", "ENDODONCIA", "datos", Map.of("nConductos", 3)));
        assertThat(creada.getResponse().getStatus()).as(creada.getResponse().getContentAsString()).isEqualTo(201);
        String id = leer(creada).get("id").asText();
        assertThat(leer(creada).get("estado").asText()).isEqualTo("BORRADOR");

        // Falta la pieza (campo obligatorio): no cierra.
        assertThat(ejecutar(post("/plantillas/registros/" + id + "/cerrar"), odontologo).getResponse().getStatus()).isEqualTo(400);

        MvcResult guardada = ejecutar(put("/plantillas/registros/" + id).contentType(MediaType.APPLICATION_JSON), odontologo,
                Map.of("datos", Map.of("pieza", 36, "nConductos", 3, "pulpar", "Necrosis pulpar")));
        assertThat(leer(guardada).get("datos").get("pieza").asInt()).isEqualTo(36);

        MvcResult cerrada = ejecutar(post("/plantillas/registros/" + id + "/cerrar"), odontologo);
        assertThat(cerrada.getResponse().getStatus()).as(cerrada.getResponse().getContentAsString()).isEqualTo(200);
        assertThat(leer(cerrada).get("estado").asText()).isEqualTo("CERRADO");

        // Cerrada: inmutable.
        assertThat(ejecutar(put("/plantillas/registros/" + id).contentType(MediaType.APPLICATION_JSON), odontologo,
                Map.of("datos", Map.of("pieza", 46))).getResponse().getStatus()).isEqualTo(409);
        assertThat(ejecutar(post("/plantillas/registros/" + id + "/cerrar"), odontologo).getResponse().getStatus()).isEqualTo(409);

        // Corrección: una ficha nueva que apunta a la cerrada.
        MvcResult correccion = ejecutar(post("/plantillas/registros").contentType(MediaType.APPLICATION_JSON), odontologo,
                Map.of("pacienteId", paciente.toString(), "plantillaCodigo", "ENDODONCIA", "corrigeId", id, "datos", Map.of("pieza", 46)));
        assertThat(correccion.getResponse().getStatus()).isEqualTo(201);
        assertThat(leer(correccion).get("corrigeId").asText()).isEqualTo(id);

        JsonNode lista = leer(ejecutar(get("/plantillas/registros/paciente/" + paciente), odontologo));
        assertThat(lista).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT datos->>'pieza' FROM registros_plantilla WHERE id = ?::uuid", String.class, id)).isEqualTo("36");
    }

    @Test
    @DisplayName("Un odontólogo no abre las fichas de un paciente que no atiende, ni quien trabaja en otra sede; recepción no entra")
    void accesoRestringido() throws Exception {
        String sede = crearSede();
        UsuarioPrueba odontologo = crearUsuario("ODONTOLOGO", sede);
        UsuarioPrueba recepcion = crearUsuario("RECEPCION", sede);
        UsuarioPrueba otraSede = crearUsuario("ADMIN_SEDE", crearSede());
        UUID ajeno = insertarPaciente(sede, "Eva", "Cano " + tag());

        assertThat(ejecutar(get("/plantillas/registros/paciente/" + ajeno), odontologo).getResponse().getStatus()).isEqualTo(403);
        assertThat(ejecutar(get("/plantillas/registros/paciente/" + ajeno), otraSede).getResponse().getStatus()).isEqualTo(403);
        assertThat(ejecutar(get("/plantillas"), recepcion).getResponse().getStatus()).isEqualTo(403);
    }
}
