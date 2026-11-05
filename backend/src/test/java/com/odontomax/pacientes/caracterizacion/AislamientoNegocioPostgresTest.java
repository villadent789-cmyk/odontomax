package com.odontomax.pacientes.caracterizacion;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * V40 y aislamiento de negocio: Villamax (centro radiológico) y Villadent (clínica) comparten el sistema pero no los
 * datos. El administrador general elige el negocio con X-Negocio y "todas las sedes" pasa a ser "todas las del negocio".
 */
@Testcontainers(disabledWithoutDocker = true)
class AislamientoNegocioPostgresTest extends PacientesPostgresTestBase {

    private String crearSedeVillamax() {
        String id = "sede-vmx-" + tag();
        jdbc.update("INSERT INTO sedes (id, nombre, direccion, sillones, activa, unidad_de_negocio) VALUES (?, ?, ?, 1, true, 'VILLAMAX')",
                id, "Villamax " + id, "Direccion " + id);
        return id;
    }

    private Set<String> ids(MvcResult r) throws Exception {
        Set<String> ids = new HashSet<>();
        JsonNode raiz = leer(r);
        JsonNode lista = raiz.isArray() ? raiz : raiz.get("content");
        lista.forEach(n -> ids.add(n.get("id").asText()));
        return ids;
    }

    @Test
    @DisplayName("V40: la unidad de negocio del paciente sale de su sede y el mismo documento cabe una vez por negocio")
    void mismoDocumentoUnaVezPorNegocio() {
        String sedeClinica = crearSede();
        String sedeRadiologia = crearSedeVillamax();
        String dni = dniUnico();

        UUID enClinica = insertarPaciente(sedeClinica, dni, "Ana", "Clinica");
        UUID enRadiologia = insertarPaciente(sedeRadiologia, dni, "Ana", "Radiologia");

        assertThat(pacienteBd(enClinica).get("unidad_de_negocio")).isEqualTo("VILLADENT");
        assertThat(pacienteBd(enRadiologia).get("unidad_de_negocio")).isEqualTo("VILLAMAX");
        assertThatThrownBy(() -> insertarPaciente(sedeClinica, dni, "Otra", "Persona"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertarPaciente(sedeRadiologia, dni, "Otra", "Persona"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("V40: al cambiar la sede del paciente a otro negocio, su unidad de negocio se actualiza sola")
    void cambiarDeSedeActualizaElNegocio() {
        String sedeClinica = crearSede();
        String sedeRadiologia = crearSedeVillamax();
        UUID id = insertarPaciente(sedeClinica, "Luis", "Cambio");

        jdbc.update("UPDATE pacientes SET sede_id = ? WHERE id = ?", sedeRadiologia, id);

        assertThat(pacienteBd(id).get("unidad_de_negocio")).isEqualTo("VILLAMAX");
    }

    @Test
    @DisplayName("Por HTTP se puede registrar el mismo DNI en los dos negocios, pero no dos veces en el mismo")
    void registrarElMismoDniEnAmbosNegocios() throws Exception {
        String sedeClinica = crearSede();
        String sedeRadiologia = crearSedeVillamax();
        UsuarioPrueba admin = adminGeneral();
        var enClinica = cuerpoPaciente(sedeClinica);
        var enRadiologia = cuerpoPaciente(sedeRadiologia);
        enRadiologia.put("dni", enClinica.get("dni"));
        var repetido = cuerpoPaciente(sedeRadiologia);
        repetido.put("dni", enClinica.get("dni"));

        assertThat(ejecutar(post("/pacientes"), admin, enClinica).getResponse().getStatus()).isEqualTo(201);
        assertThat(ejecutar(post("/pacientes"), admin, enRadiologia).getResponse().getStatus()).isEqualTo(201);
        assertThat(ejecutar(post("/pacientes"), admin, repetido).getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    @DisplayName("Pacientes: 'todas las sedes' del administrador general se acota al negocio del header X-Negocio")
    void pacientesNoMezclanNegocios() throws Exception {
        String sedeClinica = crearSede();
        String sedeRadiologia = crearSedeVillamax();
        String marca = tag();
        UUID clinico = insertarPaciente(sedeClinica, "Paciente" + marca, "Clinica");
        UUID radiologico = insertarPaciente(sedeRadiologia, "Paciente" + marca, "Radiologia");
        UsuarioPrueba admin = adminGeneral();

        Set<String> villadent = ids(ejecutar(get("/pacientes").param("busqueda", marca).header("X-Negocio", "VILLADENT"), admin));
        assertThat(villadent).contains(clinico.toString()).doesNotContain(radiologico.toString());

        Set<String> villamax = ids(ejecutar(get("/pacientes").param("busqueda", marca).header("X-Negocio", "VILLAMAX"), admin));
        assertThat(villamax).contains(radiologico.toString()).doesNotContain(clinico.toString());

        Set<String> sinFiltro = ids(ejecutar(get("/pacientes").param("busqueda", marca), admin));
        assertThat(sinFiltro).contains(clinico.toString(), radiologico.toString());

        Set<String> paginado = ids(ejecutar(get("/pacientes").param("busqueda", marca).param("page", "0").param("size", "50")
                .header("X-Negocio", "VILLAMAX"), admin));
        assertThat(paginado).contains(radiologico.toString()).doesNotContain(clinico.toString());

        Set<String> listaCompleta = ids(ejecutar(get("/pacientes").header("X-Negocio", "VILLAMAX"), admin));
        assertThat(listaCompleta).contains(radiologico.toString()).doesNotContain(clinico.toString());
    }

    @Test
    @DisplayName("Un negocio desconocido en X-Negocio es un error de petición")
    void negocioDesconocidoEsRechazado() throws Exception {
        MvcResult r = ejecutar(get("/pacientes").header("X-Negocio", "OTRO"), adminGeneral());

        assertThat(r.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("El header no amplía el alcance de quien tiene una sede: recepción sigue viendo solo la suya")
    void headerNoAmpliaElAlcanceDeLaSede() throws Exception {
        String sedeClinica = crearSede();
        String sedeRadiologia = crearSedeVillamax();
        String marca = tag();
        UUID clinico = insertarPaciente(sedeClinica, "Paciente" + marca, "Clinica");
        UUID radiologico = insertarPaciente(sedeRadiologia, "Paciente" + marca, "Radiologia");
        UsuarioPrueba recepcionClinica = crearUsuario("RECEPCION", sedeClinica);

        Set<String> propios = ids(ejecutar(get("/pacientes").param("busqueda", marca).header("X-Negocio", "VILLAMAX"), recepcionClinica));

        assertThat(propios).contains(clinico.toString()).doesNotContain(radiologico.toString());
        MvcResult ajena = ejecutar(get("/pacientes").param("sedeId", sedeRadiologia), recepcionClinica);
        assertThat(ajena.getResponse().getStatus()).isEqualTo(403);
        UsuarioPrueba recepcionRadiologia = crearUsuario("RECEPCION", sedeRadiologia);
        assertThat(ejecutar(get("/pacientes").param("sedeId", sedeClinica), recepcionRadiologia).getResponse().getStatus())
                .isEqualTo(403);
    }

    @Test
    @DisplayName("Citas: la agenda sin sede se acota al negocio activo")
    void citasNoMezclanNegocios() throws Exception {
        String sedeClinica = crearSede();
        String sedeRadiologia = crearSedeVillamax();
        LocalDate hoy = LocalDate.now();
        UUID citaClinica = insertarCita(insertarPaciente(sedeClinica, "Cita", "Clinica"), sedeClinica, "PROGRAMADA", hoy);
        UUID citaRadiologia = insertarCita(insertarPaciente(sedeRadiologia, "Cita", "Radiologia"), sedeRadiologia, "PROGRAMADA", hoy);
        UsuarioPrueba admin = adminGeneral();

        Set<String> villadent = ids(ejecutar(get("/citas").param("fecha", hoy.toString()).header("X-Negocio", "VILLADENT"), admin));
        assertThat(villadent).contains(citaClinica.toString()).doesNotContain(citaRadiologia.toString());

        Set<String> villamax = ids(ejecutar(get("/citas").param("fecha", hoy.toString()).header("X-Negocio", "VILLAMAX"), admin));
        assertThat(villamax).contains(citaRadiologia.toString()).doesNotContain(citaClinica.toString());
    }

    @Test
    @DisplayName("Laboratorio: las órdenes sin sede se acotan al negocio activo")
    void ordenesDeLaboratorioNoMezclanNegocios() throws Exception {
        String sedeClinica = crearSede();
        String sedeRadiologia = crearSedeVillamax();
        crearUsuario("ODONTOLOGO", sedeClinica);
        UUID ordenClinica = insertarOrdenLaboratorio(insertarPaciente(sedeClinica, "Lab", "Clinica"), sedeClinica);
        UUID ordenRadiologia = insertarOrdenLaboratorio(insertarPaciente(sedeRadiologia, "Lab", "Radiologia"), sedeRadiologia);
        UsuarioPrueba admin = adminGeneral();

        Set<String> villadent = ids(ejecutar(get("/laboratorio/ordenes").header("X-Negocio", "VILLADENT"), admin));
        assertThat(villadent).contains(ordenClinica.toString()).doesNotContain(ordenRadiologia.toString());

        Set<String> villamax = ids(ejecutar(get("/laboratorio/ordenes").header("X-Negocio", "VILLAMAX"), admin));
        assertThat(villamax).contains(ordenRadiologia.toString()).doesNotContain(ordenClinica.toString());
    }

    @Test
    @DisplayName("Doctores: la lista sin sede trae solo los del negocio activo")
    void doctoresNoMezclanNegocios() throws Exception {
        String sedeClinica = crearSede();
        String sedeRadiologia = crearSedeVillamax();
        UsuarioPrueba doctorClinica = crearUsuario("ODONTOLOGO", sedeClinica);
        UsuarioPrueba doctorRadiologia = crearUsuario("ODONTOLOGO", sedeRadiologia);
        UUID espClinica = jdbc.queryForObject("SELECT id FROM especialistas WHERE usuario_id = ?", UUID.class, doctorClinica.id());
        UUID espRadiologia = jdbc.queryForObject("SELECT id FROM especialistas WHERE usuario_id = ?", UUID.class, doctorRadiologia.id());
        UsuarioPrueba admin = adminGeneral();

        Set<String> villadent = ids(ejecutar(get("/especialistas").header("X-Negocio", "VILLADENT"), admin));
        assertThat(villadent).contains(espClinica.toString()).doesNotContain(espRadiologia.toString());

        Set<String> villamax = ids(ejecutar(get("/especialistas").header("X-Negocio", "VILLAMAX"), admin));
        assertThat(villamax).contains(espRadiologia.toString()).doesNotContain(espClinica.toString());
    }

    @Test
    @DisplayName("Reporte financiero: las sedes de Villamax no aparecen en la vista de Villadent")
    void reporteFinancieroPorNegocio() throws Exception {
        String sedeClinica = crearSede();
        String sedeRadiologia = crearSedeVillamax();
        insertarPago(insertarPaciente(sedeClinica, "Pago", "Clinica"), sedeClinica, "100", null);
        insertarPago(insertarPaciente(sedeRadiologia, "Pago", "Radiologia"), sedeRadiologia, "250", null);
        UsuarioPrueba admin = adminGeneral();

        Set<String> sedesVilladent = sedesDelReporte(ejecutar(get("/reportes/financiero").header("X-Negocio", "VILLADENT"), admin));
        assertThat(sedesVilladent).contains(sedeClinica).doesNotContain(sedeRadiologia);

        Set<String> sedesVillamax = sedesDelReporte(ejecutar(get("/reportes/financiero").header("X-Negocio", "VILLAMAX"), admin));
        assertThat(sedesVillamax).contains(sedeRadiologia).doesNotContain(sedeClinica);
    }


    @Test
    @DisplayName("Caja, gastos y reportes rechazan sede del otro negocio cuando X-Negocio está presente")
    void rechazaSedeDeOtroNegocioConHeader() throws Exception {
        String sedeClinica = crearSede();
        String sedeRadiologia = crearSedeVillamax();
        UsuarioPrueba admin = adminGeneral();

        // Admin con X-Negocio: VILLADENT no puede consultar caja ni gastos de Villamax
        assertThat(ejecutar(get("/caja/historial").param("sedeId", sedeRadiologia).header("X-Negocio", "VILLADENT"), admin)
                .getResponse().getStatus()).isEqualTo(403);
        assertThat(ejecutar(get("/gastos").param("sedeId", sedeRadiologia).header("X-Negocio", "VILLADENT"), admin)
                .getResponse().getStatus()).isEqualTo(403);
        assertThat(ejecutar(get("/reportes/financiero").param("sedeId", sedeRadiologia).header("X-Negocio", "VILLADENT"), admin)
                .getResponse().getStatus()).isEqualTo(403);

        // Admin con X-Negocio: VILLAMAX no puede consultar caja ni gastos de Villadent
        assertThat(ejecutar(get("/caja/historial").param("sedeId", sedeClinica).header("X-Negocio", "VILLAMAX"), admin)
                .getResponse().getStatus()).isEqualTo(403);
        assertThat(ejecutar(get("/gastos").param("sedeId", sedeClinica).header("X-Negocio", "VILLAMAX"), admin)
                .getResponse().getStatus()).isEqualTo(403);
        assertThat(ejecutar(get("/reportes/financiero").param("sedeId", sedeClinica).header("X-Negocio", "VILLAMAX"), admin)
                .getResponse().getStatus()).isEqualTo(403);
    }

    private Set<String> sedesDelReporte(MvcResult r) throws Exception {
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        Set<String> sedes = new HashSet<>();
        leer(r).get("ingresosPorSede").forEach(n -> sedes.add(n.get("sedeId").asText()));
        return sedes;
    }
}
