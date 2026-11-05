package com.odontomax.pacientes.caracterizacion;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Caracterizacion de GET /pacientes: lista sin {@code page} (array), con {@code page} (Page de Spring Data tal
 * cual), busqueda, filtro por sede por rol, semaforo de ultima cita atendida y campos derivados. Congela B-051
 * ({@code sedeId=ALL} sin page vs con page, LIKE sin escapar).
 */
@Testcontainers(disabledWithoutDocker = true)
class PacientesBusquedaCaracterizacionTest extends PacientesPostgresTestBase {

    /** GET /pacientes con los parametros de la consulta ya decodificados (el MockMvc recodificaria un %20). */
    private MockHttpServletRequestBuilder pedir(String query) {
        MockHttpServletRequestBuilder b = get("/pacientes");
        if (!query.isEmpty()) {
            for (String par : query.substring(1).split("&")) {
                String[] kv = par.split("=", 2);
                b = b.param(kv[0], URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
            }
        }
        return b;
    }

    private JsonNode listar(UsuarioPrueba u, String query) throws Exception {
        MvcResult r = ejecutar(pedir(query), u);
        assertThat(r.getResponse().getStatus()).as("GET /pacientes%s", query).isEqualTo(200);
        return leer(r);
    }

    private static List<String> idsDe(JsonNode arreglo) {
        return ids(arreglo);
    }

    // ------------------------------------------------------------------ lista sin page

    @Test
    @DisplayName("Sin page: array JSON con solo los activos de la sede, con las 29 claves por elemento")
    void listaSoloActivos() throws Exception {
        String sede = crearSede();
        UUID a = insertarPaciente(sede, "Alfa", "Uno");
        UUID b = insertarPaciente(sede, "Beta", "Dos");
        UUID archivado = insertarPaciente(sede, "Gamma", "Tres");
        jdbc.update("UPDATE pacientes SET archivado = true WHERE id = ?", archivado);
        UUID deOtraSede = insertarPaciente(crearSede(), "Delta", "Otra");

        JsonNode j = listar(adminGeneral(), "?sedeId=" + sede);

        assertThat(j.isArray()).isTrue();
        assertThat(idsDe(j)).containsExactlyInAnyOrder(a.toString(), b.toString());
        assertThat(idsDe(j)).doesNotContain(archivado.toString(), deOtraSede.toString());
        for (JsonNode n : j) {
            assertThat(campos(n)).containsExactlyInAnyOrderElementsOf(CLAVES_PACIENTE);
        }
        // 'size' sin 'page' se ignora: sigue siendo la lista completa
        assertThat(listar(adminGeneral(), "?sedeId=" + sede + "&size=1")).hasSize(2);
        // sin tope
        assertThat(listar(adminGeneral(), "?sedeId=" + sede).get(0).get("sedeId").asText()).isEqualTo(sede);
    }

    @Test
    @DisplayName("Busqueda por nombre, apellido y DNI: insensible a mayusculas y por subcadena; texto en blanco = sin busqueda")
    void busquedaPorNombreApellidoYDni() throws Exception {
        String sede = crearSede();
        UsuarioPrueba admin = adminGeneral();
        UUID mariana = insertarPaciente(sede, "Mariana", "Torres");
        UUID jose = insertarPaciente(sede, "Jose", "Villanueva");
        UUID archivada = insertarPaciente(sede, "Mariano", "Archivado");
        jdbc.update("UPDATE pacientes SET archivado = true WHERE id = ?", archivada);
        String dniJose = (String) pacienteBd(jose).get("dni");

        assertThat(idsDe(listar(admin, "?sedeId=" + sede + "&busqueda=MARI"))).containsExactly(mariana.toString());
        assertThat(idsDe(listar(admin, "?sedeId=" + sede + "&busqueda=orre"))).containsExactly(mariana.toString());
        assertThat(idsDe(listar(admin, "?sedeId=" + sede + "&busqueda=VILLA"))).containsExactly(jose.toString());
        assertThat(idsDe(listar(admin, "?sedeId=" + sede + "&busqueda=" + dniJose))).containsExactly(jose.toString());
        assertThat(idsDe(listar(admin, "?sedeId=" + sede + "&busqueda=" + dniJose.substring(1)))).containsExactly(jose.toString());
        assertThat(listar(admin, "?sedeId=" + sede + "&busqueda=zzzz")).isEmpty();
        // espacios: la busqueda sin page NO recorta ("mari " no coincide con 'Mariana')
        assertThat(listar(admin, "?sedeId=" + sede + "&busqueda=mari%20")).isEmpty();
        // en blanco = lista de activos
        assertThat(listar(admin, "?sedeId=" + sede + "&busqueda=%20%20")).hasSize(2);
    }

    @Test
    @DisplayName("La busqueda no distingue tildes ni la enie: 'maria' encuentra 'Maria con tilde' y 'MARIA' encuentra 'maria'")
    void busquedaSinTildes() throws Exception {
        String sede = crearSede();
        UsuarioPrueba admin = adminGeneral();
        UUID maria = insertarPaciente(sede, "María", "Núñez");
        UUID luis = insertarPaciente(sede, "Luis", "Perez");

        assertThat(idsDe(listar(admin, "?sedeId=" + sede + "&busqueda=maria"))).containsExactly(maria.toString());
        assertThat(idsDe(listar(admin, "?sedeId=" + sede + "&busqueda=MAR%C3%8DA"))).containsExactly(maria.toString());
        assertThat(idsDe(listar(admin, "?sedeId=" + sede + "&busqueda=nunez"))).containsExactly(maria.toString());
        assertThat(idsDe(listar(admin, "?sedeId=" + sede + "&busqueda=p%C3%A9rez"))).containsExactly(luis.toString());
    }

    @Test
    @DisplayName("B-051: busqueda con '%' (LIKE sin escapar) devuelve todos los activos de la sede")
    void busquedaConComodinSinEscapar() throws Exception {
        String sede = crearSede();
        insertarPaciente(sede, "Uno", "Comodin");
        insertarPaciente(sede, "Dos", "Comodin");
        assertThat(listar(adminGeneral(), "?sedeId=" + sede + "&busqueda=%25")).hasSize(2);
        assertThat(listar(adminGeneral(), "?sedeId=" + sede + "&busqueda=_")).as("_ = cualquier caracter").hasSize(2);
        assertThat(listar(adminGeneral(), "?sedeId=" + sede + "&busqueda=U_o")).hasSize(1);
    }

    @Test
    @DisplayName("Filtro de sede por rol: propia por defecto, explicita propia OK, ajena 403; ADMIN_GENERAL ve todas o la que pida")
    void filtroDeSedePorRol() throws Exception {
        String sedeA = crearSede();
        String sedeB = crearSede();
        UUID enA = insertarPaciente(sedeA, "En", "A");
        UUID enB = insertarPaciente(sedeB, "En", "B");

        for (String rol : List.of("ADMIN_SEDE", "ODONTOLOGO", "RECEPCION")) {
            UsuarioPrueba u = crearUsuario(rol, sedeA);
            if ("ODONTOLOGO".equals(rol)) vincularCita(enA, u);
            assertThat(idsDe(listar(u, ""))).as(rol).contains(enA.toString()).doesNotContain(enB.toString());
            assertThat(idsDe(listar(u, "?sedeId=" + sedeA))).as(rol).contains(enA.toString());
            MvcResult ajena = ejecutar(get("/pacientes?sedeId=" + sedeB), u);
            assertThat(ajena.getResponse().getStatus()).as(rol).isEqualTo(403);
            assertThat(leer(ajena).get("message").asText()).contains("no tiene permisos para acceder a la sede '" + sedeB + "'");
            // la busqueda tambien queda limitada a la sede propia
            assertThat(listar(u, "?busqueda=B")).as(rol).isNotNull();
            assertThat(idsDe(listar(u, "?busqueda=B"))).as(rol).doesNotContain(enB.toString());
        }
        UsuarioPrueba admin = adminGeneral();
        assertThat(idsDe(listar(admin, ""))).contains(enA.toString(), enB.toString());
        assertThat(idsDe(listar(admin, "?sedeId=" + sedeB))).containsExactly(enB.toString());
        assertThat(listar(admin, "?sedeId=sede-no-existe-" + tag())).isEmpty();
        assertThat(ejecutar(pedir(""), null).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("Un ODONTOLOGO sin sede asignada (la BD solo lo permite a ese rol) recibe 403 al listar; ADMIN_SEDE/RECEPCION sin sede no pueden existir")
    void usuarioSinSede() throws Exception {
        UsuarioPrueba sinSede = crearUsuario("ODONTOLOGO", null);
        for (String query : List.of("", "?page=0", "?busqueda=a")) {
            MvcResult r = ejecutar(pedir(query), sinSede);
            assertThat(r.getResponse().getStatus()).as(query).isEqualTo(403);
            assertThat(leer(r).get("message").asText()).isEqualTo("Acceso Denegado: Su usuario (" + sinSede.email()
                    + ") no tiene una sede asignada en el sistema.");
        }
        for (String rol : List.of("ADMIN_SEDE", "RECEPCION")) {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> crearUsuario(rol, null))
                    .hasMessageContaining("usuarios_rol_sede");
        }
    }

    @Test
    @DisplayName("B-051: ADMIN_GENERAL con sedeId=ALL: sin page filtra por una sede llamada 'ALL' (vacio); con page 'ALL' = todas las sedes")
    void b051SedeAll() throws Exception {
        String sede = crearSede();
        String marca = "Todas" + tag();
        UUID p = insertarPaciente(sede, marca, "Sede");
        UsuarioPrueba admin = adminGeneral();

        assertThat(listar(admin, "?sedeId=ALL")).isEmpty();
        assertThat(listar(admin, "?sedeId=ALL&busqueda=" + marca)).isEmpty();
        JsonNode paginado = listar(admin, "?sedeId=ALL&page=0&busqueda=" + marca);
        assertThat(idsDe(paginado.get("content"))).containsExactly(p.toString());
        assertThat(idsDe(listar(admin, "?sedeId=all&page=0&busqueda=" + marca).get("content"))).containsExactly(p.toString());

        // un ADMIN_SEDE no puede usar 'ALL' (se trata como una sede ajena)
        UsuarioPrueba adminSede = crearUsuario("ADMIN_SEDE", sede);
        assertThat(ejecutar(pedir("?sedeId=ALL"), adminSede).getResponse().getStatus()).isEqualTo(403);
        assertThat(ejecutar(pedir("?sedeId=ALL&page=0"), adminSede).getResponse().getStatus()).isEqualTo(403);
    }

    // ------------------------------------------------------------------ campos derivados

    @Test
    @DisplayName("Campos derivados del listado: edad, esCumpleaneroMes, avisoInasistencias (>=2), tieneDeuda y numero de historia")
    void camposDerivados() throws Exception {
        String sede = crearSede();
        LocalDate hoy = LocalDate.now();
        UUID cumple = insertarPaciente(sede, "Cumple", "Mes");
        UUID otroMes = insertarPaciente(sede, "Otro", "Mes");
        UUID sinFecha = insertarPaciente(sede, "Sin", "Fecha");
        LocalDate nacCumple = hoy.minusYears(30).withDayOfMonth(1);
        LocalDate nacOtro = hoy.minusYears(41).plusMonths(6).withDayOfMonth(1);
        jdbc.update("UPDATE pacientes SET fecha_nacimiento = ?, total_inasistencias = 2, saldo_pendiente = 0.01 WHERE id = ?", nacCumple, cumple);
        jdbc.update("UPDATE pacientes SET fecha_nacimiento = ?, total_inasistencias = 1 WHERE id = ?", nacOtro, otroMes);

        JsonNode j = listar(adminGeneral(), "?sedeId=" + sede);

        JsonNode c = buscarPorId(j, cumple.toString());
        assertThat(c.get("esCumpleaneroMes").asBoolean()).isTrue();
        assertThat(c.get("edad").asInt()).isEqualTo(Period.between(nacCumple, hoy).getYears());
        assertThat(c.get("totalInasistencias").asInt()).isEqualTo(2);
        assertThat(c.get("avisoInasistencias").asBoolean()).isTrue();
        assertThat(c.get("tieneDeuda").asBoolean()).isTrue();
        assertThat(c.get("saldoPendiente").decimalValue()).isEqualByComparingTo(new BigDecimal("0.01"));

        JsonNode o = buscarPorId(j, otroMes.toString());
        assertThat(o.get("esCumpleaneroMes").asBoolean()).isFalse();
        assertThat(o.get("edad").asInt()).isEqualTo(Period.between(nacOtro, hoy).getYears());
        assertThat(o.get("avisoInasistencias").asBoolean()).isFalse();
        assertThat(o.get("tieneDeuda").asBoolean()).isFalse();

        JsonNode s = buscarPorId(j, sinFecha.toString());
        assertThat(s.get("fechaNacimiento").isNull()).isTrue();
        assertThat(s.get("edad").asInt()).isZero();
        assertThat(s.get("esCumpleaneroMes").asBoolean()).isFalse();
        assertThat(s.get("numeroHistoriaClinica").asText()).isEqualTo("HC-" + pacienteBd(sinFecha).get("dni"));
        assertThat(s.get("alergias")).isEmpty();
    }

    @Test
    @DisplayName("ultimaCitaAtendida: fecha de la ultima cita ATENDIDA del propio paciente (ignora otros estados y otros pacientes), en lista y en page")
    void ultimaCitaAtendida() throws Exception {
        String sede = crearSede();
        UUID conAtendidas = insertarPaciente(sede, "Atendido", "Varias");
        UUID soloProgramada = insertarPaciente(sede, "Programado", "Solo");
        UUID unaVieja = insertarPaciente(sede, "Atendido", "Vieja");
        UUID sinCitas = insertarPaciente(sede, "Sin", "Citas");
        LocalDate hoy = LocalDate.now();
        insertarCita(conAtendidas, sede, "ATENDIDA", hoy.minusDays(10));
        insertarCita(conAtendidas, sede, "ATENDIDA", hoy.minusDays(3));
        insertarCita(conAtendidas, sede, "PROGRAMADA", hoy.plusDays(1));
        insertarCita(conAtendidas, sede, "CANCELADA", hoy.minusDays(1));
        insertarCita(soloProgramada, sede, "PROGRAMADA", hoy.plusDays(2));
        insertarCita(soloProgramada, sede, "NO_ASISTIO", hoy.minusDays(2));
        insertarCita(unaVieja, sede, "ATENDIDA", hoy.minusDays(40));

        UsuarioPrueba admin = adminGeneral();
        JsonNode lista = listar(admin, "?sedeId=" + sede);
        assertThat(buscarPorId(lista, conAtendidas.toString()).get("ultimaCitaAtendida").asText()).isEqualTo(hoy.minusDays(3).toString());
        assertThat(buscarPorId(lista, unaVieja.toString()).get("ultimaCitaAtendida").asText()).isEqualTo(hoy.minusDays(40).toString());
        assertThat(buscarPorId(lista, soloProgramada.toString()).get("ultimaCitaAtendida").isNull()).isTrue();
        assertThat(buscarPorId(lista, sinCitas.toString()).get("ultimaCitaAtendida").isNull()).isTrue();

        JsonNode page = listar(admin, "?sedeId=" + sede + "&page=0&size=10").get("content");
        assertThat(buscarPorId(page, conAtendidas.toString()).get("ultimaCitaAtendida").asText()).isEqualTo(hoy.minusDays(3).toString());
        assertThat(buscarPorId(page, soloProgramada.toString()).get("ultimaCitaAtendida").isNull()).isTrue();
    }

    // ------------------------------------------------------------------ paginacion

    private String sedeConVeinticincoPacientes() {
        String sede = crearSede();
        for (int i = 0; i < 25; i++) {
            insertarPaciente(sede, String.format("Pag%02d", i), "Paginado");
        }
        return sede;
    }

    @Test
    @DisplayName("Con page: JSON del Page de Spring Data con sus claves exactas; size por defecto 20; orden por nombres")
    void paginaConClavesExactas() throws Exception {
        String sede = sedeConVeinticincoPacientes();
        JsonNode j = listar(adminGeneral(), "?sedeId=" + sede + "&page=0");

        assertThat(campos(j)).containsExactlyInAnyOrder("content", "pageable", "last", "totalPages", "totalElements",
                "size", "number", "sort", "first", "numberOfElements", "empty");
        assertThat(campos(j.get("pageable"))).containsExactlyInAnyOrder("pageNumber", "pageSize", "sort", "offset",
                "paged", "unpaged");
        assertThat(campos(j.get("pageable").get("sort"))).containsExactlyInAnyOrder("empty", "sorted", "unsorted");
        assertThat(campos(j.get("sort"))).containsExactlyInAnyOrder("empty", "sorted", "unsorted");
        assertThat(j.get("sort").get("sorted").asBoolean()).isTrue();
        assertThat(j.get("content")).hasSize(20);
        assertThat(j.get("size").asInt()).isEqualTo(20);
        assertThat(j.get("number").asInt()).isZero();
        assertThat(j.get("totalElements").asInt()).isEqualTo(25);
        assertThat(j.get("totalPages").asInt()).isEqualTo(2);
        assertThat(j.get("numberOfElements").asInt()).isEqualTo(20);
        assertThat(j.get("first").asBoolean()).isTrue();
        assertThat(j.get("last").asBoolean()).isFalse();
        assertThat(j.get("empty").asBoolean()).isFalse();
        assertThat(j.get("pageable").get("pageSize").asInt()).isEqualTo(20);
        assertThat(j.get("pageable").get("offset").asInt()).isZero();
        assertThat(j.get("pageable").get("paged").asBoolean()).isTrue();
        List<String> nombres = new ArrayList<>();
        j.get("content").forEach(n -> nombres.add(n.get("nombres").asText()));
        List<String> esperados = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            esperados.add(String.format("Pag%02d", i));
        }
        assertThat(nombres).containsExactlyElementsOf(esperados);
        for (JsonNode n : j.get("content")) {
            assertThat(campos(n)).containsExactlyInAnyOrderElementsOf(CLAVES_PACIENTE);
        }
    }

    @Test
    @DisplayName("size: 0, negativo o mayor de 100 vuelven a 20; 100 es valido; page negativo = 0; paginas siguientes y vacias")
    void tamanoYNumeroDePagina() throws Exception {
        String sede = sedeConVeinticincoPacientes();
        UsuarioPrueba admin = adminGeneral();
        String base = "?sedeId=" + sede;

        for (String size : List.of("0", "-5", "101", "1000")) {
            JsonNode j = listar(admin, base + "&page=0&size=" + size);
            assertThat(j.get("size").asInt()).as("size=" + size).isEqualTo(20);
            assertThat(j.get("content")).as("size=" + size).hasSize(20);
        }
        JsonNode cien = listar(admin, base + "&page=0&size=100");
        assertThat(cien.get("size").asInt()).isEqualTo(100);
        assertThat(cien.get("content")).hasSize(25);
        assertThat(cien.get("totalPages").asInt()).isEqualTo(1);
        assertThat(cien.get("last").asBoolean()).isTrue();

        JsonNode negativa = listar(admin, base + "&page=-1&size=10");
        assertThat(negativa.get("number").asInt()).isZero();
        assertThat(negativa.get("first").asBoolean()).isTrue();

        JsonNode p1 = listar(admin, base + "&page=1&size=10");
        assertThat(p1.get("number").asInt()).isEqualTo(1);
        assertThat(p1.get("content")).hasSize(10);
        assertThat(p1.get("content").get(0).get("nombres").asText()).isEqualTo("Pag10");
        assertThat(p1.get("first").asBoolean()).isFalse();
        assertThat(p1.get("last").asBoolean()).isFalse();
        assertThat(p1.get("pageable").get("offset").asInt()).isEqualTo(10);

        JsonNode p2 = listar(admin, base + "&page=2&size=10");
        assertThat(p2.get("content")).hasSize(5);
        assertThat(p2.get("last").asBoolean()).isTrue();
        assertThat(p2.get("numberOfElements").asInt()).isEqualTo(5);

        JsonNode fuera = listar(admin, base + "&page=9&size=10");
        assertThat(fuera.get("content")).isEmpty();
        assertThat(fuera.get("empty").asBoolean()).isTrue();
        assertThat(fuera.get("totalElements").asInt()).isEqualTo(25);
    }

    @Test
    @DisplayName("Con page: la busqueda recorta espacios, es insensible a mayusculas y no incluye archivados; blanco = sin filtro")
    void paginaConBusquedaYArchivados() throws Exception {
        String sede = crearSede();
        UsuarioPrueba admin = adminGeneral();
        UUID a = insertarPaciente(sede, "Zacarias", "Uno");
        insertarPaciente(sede, "Otro", "Dos");
        UUID archivado = insertarPaciente(sede, "Zacarina", "Archivada");
        jdbc.update("UPDATE pacientes SET archivado = true WHERE id = ?", archivado);

        JsonNode j = listar(admin, "?sedeId=" + sede + "&page=0&busqueda=%20ZACAR%20");
        assertThat(idsDe(j.get("content"))).containsExactly(a.toString());
        assertThat(j.get("totalElements").asInt()).isEqualTo(1);
        assertThat(listar(admin, "?sedeId=" + sede + "&page=0&busqueda=%20%20").get("totalElements").asInt()).isEqualTo(2);
        assertThat(listar(admin, "?sedeId=" + sede + "&page=0").get("totalElements").asInt()).isEqualTo(2);
    }

    @Test
    @DisplayName("Con page por rol: sede propia por defecto, ajena 403 y sin token 401")
    void paginaPorRol() throws Exception {
        String sedeA = crearSede();
        String sedeB = crearSede();
        UUID enA = insertarPaciente(sedeA, "Pag", "A");
        insertarPaciente(sedeB, "Pag", "B");
        for (String rol : List.of("ADMIN_SEDE", "ODONTOLOGO", "RECEPCION")) {
            UsuarioPrueba u = crearUsuario(rol, sedeA);
            if ("ODONTOLOGO".equals(rol)) vincularCita(enA, u);
            JsonNode j = listar(u, "?page=0&size=50");
            assertThat(idsDe(j.get("content"))).as(rol).contains(enA.toString());
            for (JsonNode n : j.get("content")) {
                assertThat(n.get("sedeId").asText()).as(rol).isEqualTo(sedeA);
            }
            assertThat(ejecutar(pedir("?page=0&sedeId=" + sedeB), u).getResponse().getStatus()).as(rol).isEqualTo(403);
        }
        assertThat(ejecutar(pedir("?page=0"), null).getResponse().getStatus()).isEqualTo(401);
    }
}
