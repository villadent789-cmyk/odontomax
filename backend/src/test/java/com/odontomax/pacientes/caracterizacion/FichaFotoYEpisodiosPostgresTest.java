package com.odontomax.pacientes.caracterizacion;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Historia clínica general, foto del paciente y episodios del odontograma contra Postgres real: el documento jsonb, el
 * binario de la foto y las fotografías inicial y final del odontograma.
 */
@Testcontainers(disabledWithoutDocker = true)
class FichaFotoYEpisodiosPostgresTest extends PacientesPostgresTestBase {

    private static final String SEDE = "sede-chilca-1";
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F', 0, 1};

    @Test
    @DisplayName("La ficha de ingreso se guarda como documento y se lee igual, con autor y fecha del servidor")
    void fichaDeIngreso() throws Exception {
        UsuarioPrueba admin = adminGeneral();
        UUID paciente = insertarPaciente(SEDE, "Rosa", "Quispe " + tag());

        JsonNode vacia = leer(ejecutar(get("/pacientes/" + paciente + "/ficha-ingreso"), admin));
        assertThat(vacia.has("emergenciaNombre")).isFalse();

        MvcResult guardada = ejecutar(put("/pacientes/" + paciente + "/ficha-ingreso"), admin, Map.of(
                "emergenciaNombre", "Marta Vera", "emergenciaTelefono", "964123456", "emergenciaParentesco", "Madre",
                "bruxismo", true, "hijos", 2, "ultimaProfilaxis", "2026-05-10"));
        assertThat(guardada.getResponse().getStatus()).as(guardada.getResponse().getContentAsString()).isEqualTo(200);

        JsonNode leida = leer(ejecutar(get("/pacientes/" + paciente + "/ficha-ingreso"), admin));
        assertThat(leida.get("emergenciaParentesco").asText()).isEqualTo("Madre");
        assertThat(leida.get("bruxismo").asBoolean()).isTrue();
        assertThat(leida.get("hijos").asInt()).isEqualTo(2);
        assertThat(leida.get("ultimaProfilaxis").asText()).isEqualTo("2026-05-10");
        assertThat(leida.get("actualizadoPorNombre").asText()).isNotBlank();
        assertThat(contar("SELECT count(*) FROM pacientes_ficha_ingreso WHERE paciente_id = ?", paciente)).isEqualTo(1);

        // Guardar otra vez reemplaza el documento, no lo duplica.
        ejecutar(put("/pacientes/" + paciente + "/ficha-ingreso"), admin, Map.of("emergenciaNombre", "Otra persona"));
        assertThat(contar("SELECT count(*) FROM pacientes_ficha_ingreso WHERE paciente_id = ?", paciente)).isEqualTo(1);
        assertThat(leer(ejecutar(get("/pacientes/" + paciente + "/ficha-ingreso"), admin)).get("emergenciaNombre").asText())
                .isEqualTo("Otra persona");
    }

    @Test
    @DisplayName("La foto se sube, se lee con su tipo real, marca al paciente y se puede quitar")
    void fotoDelPaciente() throws Exception {
        UsuarioPrueba admin = adminGeneral();
        UUID paciente = insertarPaciente(SEDE, "Luis", "Meza " + tag());

        assertThat(ejecutar(get("/pacientes/" + paciente + "/foto"), admin).getResponse().getStatus()).isEqualTo(404);

        MvcResult subida = ejecutar(multipart(org.springframework.http.HttpMethod.PUT, "/pacientes/" + paciente + "/foto")
                .file(new MockMultipartFile("archivo", "yo.png", "image/png", JPEG)), admin);
        assertThat(subida.getResponse().getStatus()).as(subida.getResponse().getContentAsString()).isEqualTo(200);

        MvcResult leida = ejecutar(get("/pacientes/" + paciente + "/foto"), admin);
        assertThat(leida.getResponse().getContentType()).isEqualTo("image/jpeg");
        assertThat(leida.getResponse().getContentAsByteArray()).isEqualTo(JPEG);
        assertThat(pacienteBd(paciente).get("foto_actualizada_en")).isNotNull();
        assertThat(leer(ejecutar(get("/pacientes/" + paciente), admin)).get("fotoActualizadaEn").asText()).isNotBlank();

        MvcResult falsa = ejecutar(multipart(org.springframework.http.HttpMethod.PUT, "/pacientes/" + paciente + "/foto")
                .file(new MockMultipartFile("archivo", "x.jpg", "image/jpeg", "no soy una imagen".getBytes())), admin);
        assertThat(falsa.getResponse().getStatus()).isEqualTo(400);

        assertThat(ejecutar(delete("/pacientes/" + paciente + "/foto"), admin).getResponse().getStatus()).isEqualTo(204);
        assertThat(ejecutar(get("/pacientes/" + paciente + "/foto"), admin).getResponse().getStatus()).isEqualTo(404);
        assertThat(pacienteBd(paciente).get("foto_actualizada_en")).isNull();
    }

    @Test
    @DisplayName("Un episodio guarda el odontograma inicial al abrirse y el final al cerrarse; no hay dos abiertos a la vez")
    void episodiosDelOdontograma() throws Exception {
        UsuarioPrueba admin = adminGeneral();
        UUID paciente = insertarPaciente(SEDE, "Carlos", "Huamán " + tag());
        insertarOdontograma(paciente, 16);

        MvcResult abierto = ejecutar(post("/odontograma/pacientes/" + paciente + "/episodios"), admin, Map.of("motivo", "Dolor de muela"));
        assertThat(abierto.getResponse().getStatus()).as(abierto.getResponse().getContentAsString()).isEqualTo(201);
        JsonNode episodio = leer(abierto);
        assertThat(episodio.get("numero").asInt()).isEqualTo(1);
        assertThat(episodio.get("estado").asText()).isEqualTo("ABIERTO");
        assertThat(episodio.get("inicial")).hasSize(1);
        assertThat(episodio.get("inicial").get(0).get("piezaFdi").asInt()).isEqualTo(16);
        String id = episodio.get("id").asText();

        // Mientras haya uno abierto no se abre otro.
        assertThat(ejecutar(post("/odontograma/pacientes/" + paciente + "/episodios"), admin, Map.of()).getResponse().getStatus()).isEqualTo(409);

        // Durante el tratamiento cambia el odontograma; el inicial queda fijo.
        jdbc.update("INSERT INTO odontograma_registros (paciente_id, pieza_fdi, estado, caras) VALUES (?, 26, 'caries', '{}')", paciente);
        MvcResult cerrado = ejecutar(post("/odontograma/pacientes/" + paciente + "/episodios/" + id + "/cerrar"), admin, Map.of("nota", "Alta sin molestias"));
        assertThat(cerrado.getResponse().getStatus()).as(cerrado.getResponse().getContentAsString()).isEqualTo(200);
        JsonNode alta = leer(cerrado);
        assertThat(alta.get("estado").asText()).isEqualTo("CERRADO");
        assertThat(alta.get("inicial")).hasSize(1);
        assertThat(alta.get("finalPiezas")).hasSize(2);
        assertThat(alta.get("notaFinal").asText()).isEqualTo("Alta sin molestias");

        assertThat(ejecutar(post("/odontograma/pacientes/" + paciente + "/episodios/" + id + "/cerrar"), admin, Map.of()).getResponse().getStatus()).isEqualTo(409);

        // Vuelve a los 3 meses: segundo episodio, y la lista los trae del más nuevo al más antiguo.
        MvcResult segundo = ejecutar(post("/odontograma/pacientes/" + paciente + "/episodios"), admin, Map.of("motivo", "Control a los 3 meses"));
        assertThat(leer(segundo).get("numero").asInt()).isEqualTo(2);
        JsonNode lista = leer(ejecutar(get("/odontograma/pacientes/" + paciente + "/episodios"), admin));
        assertThat(lista).hasSize(2);
        assertThat(lista.get(0).get("numero").asInt()).isEqualTo(2);
    }
}
