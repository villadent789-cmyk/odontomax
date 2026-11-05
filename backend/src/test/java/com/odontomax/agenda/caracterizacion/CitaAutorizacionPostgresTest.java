package com.odontomax.agenda.caracterizacion;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * B-035 (Fase 3.6): autorización de citas con pila completa (filtro JWT real, @PreAuthorize, servicios y PostgreSQL).
 * Un ODONTOLOGO solo ve y atiende sus propias citas, no cancela ni reprograma, y nadie opera citas de otra sede.
 */
@Testcontainers(disabledWithoutDocker = true)
class CitaAutorizacionPostgresTest extends AgendaPostgresFixtures {

    private static final String FECHA_PARAM = "fecha";

    private List<String> idsDe(MvcResult r) throws Exception {
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(200);
        List<String> ids = new ArrayList<>();
        JsonNode arreglo = leer(r);
        arreglo.forEach(n -> ids.add(n.get("id").asText()));
        return ids;
    }

    private MvcResult listar(UsuarioPrueba u, LocalDate fecha) throws Exception {
        return mockMvc.perform(get("/citas").param(FECHA_PARAM, fecha.toString()).header("Authorization", u.bearer())).andReturn();
    }

    private MvcResult patchJson(UsuarioPrueba u, String ruta, String cuerpo) throws Exception {
        return mockMvc.perform(patch(ruta).header("Authorization", u.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andReturn();
    }

    @Test
    @DisplayName("GET /citas y /citas?desde&hasta: un ODONTOLOGO solo ve las citas de su especialista; sin especialista vinculado, lista vacía; RECEPCION sigue viendo toda la sede")
    void odontologoSoloVeSusCitas() throws Exception {
        LocalDate fecha = fechaUnica();
        UsuarioPrueba odonto = crearUsuario("ODONTOLOGO", SEDE_CHILCA_1);
        UsuarioPrueba sinPerfil = crearUsuario("ODONTOLOGO", SEDE_CHILCA_1);
        UsuarioPrueba recepcion = crearUsuario("RECEPCION", SEDE_CHILCA_1);
        UsuarioPrueba adminSede = crearUsuario("ADMIN_SEDE", SEDE_CHILCA_1);
        UsuarioPrueba adminGeneral = adminGeneral();
        UUID propio = crearEspecialista(odonto.id(), SEDE_CHILCA_1);
        UUID otroDoctor = crearEspecialista(null, SEDE_CHILCA_1);
        UUID paciente = crearPaciente(SEDE_CHILCA_1);

        UUID citaPropia = insertarCita(paciente, propio, SEDE_CHILCA_1, 1, fecha, LocalTime.of(9, 0), 30, "PROGRAMADA");
        UUID citaAjena = insertarCita(paciente, otroDoctor, SEDE_CHILCA_1, 2, fecha, LocalTime.of(9, 0), 30, "PROGRAMADA");
        UUID citaSinDoctor = insertarCita(paciente, null, SEDE_CHILCA_1, null, fecha, LocalTime.of(10, 0), 30, "PROGRAMADA");

        assertThat(idsDe(listar(odonto, fecha))).containsExactly(citaPropia.toString());
        assertThat(idsDe(mockMvc.perform(get("/citas").param("desde", fecha.toString()).param("hasta", fecha.toString())
                .header("Authorization", odonto.bearer())).andReturn())).containsExactly(citaPropia.toString());
        assertThat(idsDe(listar(sinPerfil, fecha))).isEmpty();

        for (UsuarioPrueba u : List.of(recepcion, adminSede, adminGeneral)) {
            assertThat(idsDe(listar(u, fecha)))
                    .as("rol %s", u.rol())
                    .contains(citaPropia.toString(), citaAjena.toString(), citaSinDoctor.toString());
        }
    }

    @Test
    @DisplayName("GET /citas/lista-espera y /citas/reservas-web-pendientes: el ODONTOLOGO solo recibe las citas de su especialista")
    void odontologoListasAuxiliares() throws Exception {
        UsuarioPrueba odonto = crearUsuario("ODONTOLOGO", SEDE_CHILCA_1);
        UUID propio = crearEspecialista(odonto.id(), SEDE_CHILCA_1);
        UUID otroDoctor = crearEspecialista(null, SEDE_CHILCA_1);
        UUID paciente = crearPaciente(SEDE_CHILCA_1);
        LocalDate hoy = LocalDate.now(java.time.ZoneId.of("America/Lima"));

        UUID enSalaPropia = insertarCita(paciente, propio, SEDE_CHILCA_1, null, hoy, LocalTime.of(7, 0), 5, "EN_SALA");
        UUID enSalaAjena = insertarCita(paciente, otroDoctor, SEDE_CHILCA_1, null, hoy, LocalTime.of(7, 10), 5, "EN_SALA");
        UUID webPropia = insertarCita(paciente, propio, SEDE_CHILCA_1, null, fechaUnica(), LocalTime.of(9, 30), 30, "PENDIENTE_WEB");
        UUID webAjena = insertarCita(paciente, otroDoctor, SEDE_CHILCA_1, null, fechaUnica(), LocalTime.of(9, 30), 30, "PENDIENTE_WEB");

        List<String> sala = idsDe(mockMvc.perform(get("/citas/lista-espera").header("Authorization", odonto.bearer())).andReturn());
        assertThat(sala).contains(enSalaPropia.toString()).doesNotContain(enSalaAjena.toString());

        List<String> web = idsDe(mockMvc.perform(get("/citas/reservas-web-pendientes").header("Authorization", odonto.bearer())).andReturn());
        assertThat(web).contains(webPropia.toString()).doesNotContain(webAjena.toString());
    }

    @Test
    @DisplayName("PATCH /citas/{id}/cancelar y /reprogramar: ODONTOLOGO recibe 403 incluso sobre su propia cita; RECEPCION, ADMIN_SEDE y ADMIN_GENERAL siguen pudiendo")
    void odontologoNoCancelaNiReprograma() throws Exception {
        String sede = "sede-aut-" + tag();
        jdbc.update("INSERT INTO sedes (id, nombre, direccion, ciudad, telefono, unidad_de_negocio, sillones, activa) "
                + "VALUES (?, 'Sede Aut', 'Dir', 'Huancayo', '999', 'VILLADENT', 4, true)", sede);
        UsuarioPrueba odonto = crearUsuario("ODONTOLOGO", sede);
        UsuarioPrueba recepcion = crearUsuario("RECEPCION", sede);
        UUID propio = crearEspecialista(odonto.id(), sede);
        asignarSillon(propio, sede, 1);
        UUID paciente = crearPaciente(sede);
        LocalDate fecha = fechaUnica();
        UUID cita = insertarCita(paciente, propio, sede, 1, fecha, LocalTime.of(9, 0), 30, "PROGRAMADA");

        String reprogramar = "{\"nuevaFecha\":\"" + fecha.plusDays(1) + "\",\"nuevoHorario\":\"10:00\",\"motivo\":\"Cambio pedido por el paciente\"}";
        assertThat(patchJson(odonto, "/citas/" + cita + "/cancelar", "{\"motivo\":\"Paciente no puede venir\"}").getResponse().getStatus()).isEqualTo(403);
        assertThat(patchJson(odonto, "/citas/" + cita + "/reprogramar", reprogramar).getResponse().getStatus()).isEqualTo(403);
        assertThat(estadoDe(cita)).isEqualTo("PROGRAMADA");

        MvcResult rep = patchJson(recepcion, "/citas/" + cita + "/reprogramar", reprogramar);
        assertThat(rep.getResponse().getStatus()).as(rep.getResponse().getContentAsString()).isEqualTo(200);
        MvcResult can = patchJson(recepcion, "/citas/" + cita + "/cancelar", "{\"motivo\":\"Paciente no puede venir\"}");
        assertThat(can.getResponse().getStatus()).as(can.getResponse().getContentAsString()).isEqualTo(200);
        assertThat(estadoDe(cita)).isEqualTo("CANCELADA");
    }

    @Test
    @DisplayName("PATCH /citas/{id}/estado: el ODONTOLOGO solo marca ATENDIDA sobre sus propias citas; EN_SALA, CONFIRMADA y CANCELADA o una cita ajena dan 403")
    void odontologoSoloAtiendeSusCitas() throws Exception {
        UsuarioPrueba odonto = crearUsuario("ODONTOLOGO", SEDE_CHILCA_1);
        UsuarioPrueba sinPerfil = crearUsuario("ODONTOLOGO", SEDE_CHILCA_1);
        UUID propio = crearEspecialista(odonto.id(), SEDE_CHILCA_1);
        UUID otroDoctor = crearEspecialista(null, SEDE_CHILCA_1);
        UUID paciente = crearPaciente(SEDE_CHILCA_1);
        LocalDate fecha = fechaUnica();

        UUID suya = insertarCita(paciente, propio, SEDE_CHILCA_1, null, fecha, LocalTime.of(9, 0), 30, "EN_ATENCION");
        UUID ajena = insertarCita(paciente, otroDoctor, SEDE_CHILCA_1, null, fecha, LocalTime.of(10, 0), 30, "EN_ATENCION");
        UUID sinDoctor = insertarCita(paciente, null, SEDE_CHILCA_1, null, fecha, LocalTime.of(11, 0), 30, "EN_ATENCION");
        UUID programada = insertarCita(paciente, propio, SEDE_CHILCA_1, null, fecha, LocalTime.of(12, 0), 30, "PROGRAMADA");
        UUID confirmada = insertarCita(paciente, propio, SEDE_CHILCA_1, null, fecha, LocalTime.of(13, 0), 30, "CONFIRMADA");

        assertThat(patchJson(odonto, "/citas/" + ajena + "/estado", "{\"estado\":\"ATENDIDA\"}").getResponse().getStatus()).isEqualTo(403);
        assertThat(patchJson(odonto, "/citas/" + sinDoctor + "/estado", "{\"estado\":\"ATENDIDA\"}").getResponse().getStatus()).isEqualTo(403);
        assertThat(patchJson(sinPerfil, "/citas/" + suya + "/estado", "{\"estado\":\"ATENDIDA\"}").getResponse().getStatus()).isEqualTo(403);
        assertThat(patchJson(odonto, "/citas/" + programada + "/estado", "{\"estado\":\"EN_SALA\"}").getResponse().getStatus()).isEqualTo(403);
        assertThat(patchJson(odonto, "/citas/" + programada + "/estado", "{\"estado\":\"CONFIRMADA\"}").getResponse().getStatus()).isEqualTo(403);
        assertThat(patchJson(odonto, "/citas/" + confirmada + "/estado", "{\"estado\":\"CANCELADA\",\"motivo\":\"Cancelada por el doctor\"}").getResponse().getStatus()).isEqualTo(403);
        assertThat(patchJson(odonto, "/citas/" + confirmada + "/estado", "{\"estado\":\"NO_ASISTIO\"}").getResponse().getStatus()).isEqualTo(403);
        assertThat(estadoDe(ajena)).isEqualTo("EN_ATENCION");
        assertThat(estadoDe(sinDoctor)).isEqualTo("EN_ATENCION");
        assertThat(estadoDe(suya)).isEqualTo("EN_ATENCION");
        assertThat(estadoDe(programada)).isEqualTo("PROGRAMADA");
        assertThat(estadoDe(confirmada)).isEqualTo("CONFIRMADA");

        MvcResult ok = patchJson(odonto, "/citas/" + suya + "/estado", "{\"estado\":\"ATENDIDA\"}");
        assertThat(ok.getResponse().getStatus()).as(ok.getResponse().getContentAsString()).isEqualTo(200);
        assertThat(estadoDe(suya)).isEqualTo("ATENDIDA");
    }

    @Test
    @DisplayName("Los demás roles conservan sus permisos de estado: RECEPCION confirma y marca EN_SALA, ADMIN_SEDE marca EN_SALA (sin cambios)")
    void otrosRolesSinCambios() throws Exception {
        UsuarioPrueba recepcion = crearUsuario("RECEPCION", SEDE_CHILCA_1);
        UsuarioPrueba adminSede = crearUsuario("ADMIN_SEDE", SEDE_CHILCA_1);
        UUID paciente = crearPaciente(SEDE_CHILCA_1);
        LocalDate fecha = fechaUnica();
        UUID a = insertarCita(paciente, null, SEDE_CHILCA_1, null, fecha, LocalTime.of(9, 0), 30, "PROGRAMADA");
        UUID b = insertarCita(paciente, null, SEDE_CHILCA_1, null, fecha, LocalTime.of(10, 0), 30, "CONFIRMADA");

        assertThat(patchJson(recepcion, "/citas/" + a + "/estado", "{\"estado\":\"CONFIRMADA\"}").getResponse().getStatus()).isEqualTo(200);
        assertThat(patchJson(adminSede, "/citas/" + b + "/estado", "{\"estado\":\"EN_SALA\"}").getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Aislamiento por sede: RECEPCION y ADMIN_SEDE de otra sede reciben 403 en estado, cancelar, reprogramar, adelanto y contacto, y la cita no cambia")
    void aislamientoPorSede() throws Exception {
        UUID paciente = crearPaciente(SEDE_CHILCA_1);
        LocalDate fecha = fechaUnica();
        UUID cita = insertarCita(paciente, null, SEDE_CHILCA_1, null, fecha, LocalTime.of(9, 0), 30, "PROGRAMADA");

        for (String rol : List.of("RECEPCION", "ADMIN_SEDE")) {
            UsuarioPrueba intruso = crearUsuario(rol, SEDE_ELTAMBO);
            String base = "/citas/" + cita;
            assertThat(patchJson(intruso, base + "/estado", "{\"estado\":\"CONFIRMADA\"}").getResponse().getStatus()).as(rol + " estado").isEqualTo(403);
            assertThat(patchJson(intruso, base + "/cancelar", "{\"motivo\":\"Intento de otra sede\"}").getResponse().getStatus()).as(rol + " cancelar").isEqualTo(403);
            assertThat(patchJson(intruso, base + "/reprogramar",
                    "{\"nuevaFecha\":\"" + fecha.plusDays(1) + "\",\"nuevoHorario\":\"10:00\",\"motivo\":\"Intento de otra sede\"}").getResponse().getStatus())
                    .as(rol + " reprogramar").isEqualTo(403);
            assertThat(mockMvc.perform(post(base + "/adelanto").header("Authorization", intruso.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"medioPago\":\"YAPE\",\"monto\":30,\"numeroOperacion\":\"123456\"}")).andReturn().getResponse().getStatus())
                    .as(rol + " adelanto").isEqualTo(403);
            assertThat(mockMvc.perform(post(base + "/contacto").header("Authorization", intruso.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"medio\":\"LLAMADA\",\"resultado\":\"CONFIRMO\"}")).andReturn().getResponse().getStatus())
                    .as(rol + " contacto").isEqualTo(403);
            assertThat(mockMvc.perform(get("/citas").param(FECHA_PARAM, fecha.toString()).param("sedeId", SEDE_CHILCA_1)
                    .header("Authorization", intruso.bearer())).andReturn().getResponse().getStatus()).as(rol + " listado de otra sede").isEqualTo(403);
        }
        assertThat(estadoDe(cita)).isEqualTo("PROGRAMADA");
        assertThat(jdbc.queryForObject("SELECT notas FROM citas WHERE id = ?", String.class, cita)).isNull();
    }

    @Test
    @DisplayName("Aislamiento por sede: el listado de RECEPCION de una sede ignora las citas de otra sede aunque tengan la misma fecha")
    void listadoNoMezclaSedes() throws Exception {
        UsuarioPrueba recepcionTambo = crearUsuario("RECEPCION", SEDE_ELTAMBO);
        UUID pacienteChilca = crearPaciente(SEDE_CHILCA_1);
        LocalDate fecha = fechaUnica();
        UUID citaChilca = insertarCita(pacienteChilca, null, SEDE_CHILCA_1, null, fecha, LocalTime.of(9, 0), 30, "PROGRAMADA");

        assertThat(idsDe(listar(recepcionTambo, fecha))).doesNotContain(citaChilca.toString());
    }
}
