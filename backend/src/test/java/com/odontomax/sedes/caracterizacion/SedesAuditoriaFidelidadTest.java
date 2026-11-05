package com.odontomax.sedes.caracterizacion;

import com.odontomax.sedes.dto.SedeRequestDTO;
import com.odontomax.sedes.dto.ServicioRequestDTO;
import com.odontomax.sedes.service.SedeService;
import com.odontomax.sedes.service.ServicioService;
import com.odontomax.identidad.api.ClinicaUserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Fidelidad de la auditoría de sedes y servicios (plan de sedes, secciones 5 y 8.3; riesgo R2): las 10 columnas
 * que escriben {@code SedeService.registrarAuditoria} y {@code ServicioService.registrarAuditoria} para las 8
 * acciones, con y sin principal. Las filas se buscan por {@code accion} de aplicación porque la tabla sedes tiene
 * además su trigger {@code sedes_auditoria} (acciones INSERT/UPDATE). Protege el paso P5.
 */
@Testcontainers(disabledWithoutDocker = true)
class SedesAuditoriaFidelidadTest extends SedesPostgresTestBase {

    private static final String IP = "10.77.5.9";

    @Autowired
    private SedeService sedeService;

    @Autowired
    private ServicioService servicioService;

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    private Map<String, Object> fila(String accion, String registroId) {
        List<Map<String, Object>> filas = jdbc.queryForList("SELECT usuario_id, usuario_nombre, rol, sede_id, modulo, "
                + "accion, tabla, registro_id, detalle, host(ip) AS ip FROM auditoria_log WHERE accion = ? "
                + "AND registro_id = ?", accion, registroId);
        assertThat(filas).as("filas de %s para %s", accion, registroId).hasSize(1);
        Map<String, Object> f = new HashMap<>(filas.get(0));
        if (f.get("usuario_id") != null) {
            f.put("usuario_id", f.get("usuario_id").toString());
        }
        return f;
    }

    private static Map<String, Object> esperado(Object usuarioId, String nombre, String rol, String sede, String accion,
                                                String tabla, String registroId, String detalle, String ip) {
        Map<String, Object> m = new HashMap<>();
        m.put("usuario_id", usuarioId == null ? null : usuarioId.toString());
        m.put("usuario_nombre", nombre);
        m.put("rol", rol);
        m.put("sede_id", sede);
        m.put("modulo", "CONFIGURACION");
        m.put("accion", accion);
        m.put("tabla", tabla);
        m.put("registro_id", registroId);
        m.put("detalle", detalle);
        m.put("ip", ip);
        return m;
    }

    private int filasDeRegistro(String registroId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM auditoria_log WHERE registro_id = ?", Integer.class, registroId);
        return n == null ? 0 : n;
    }

    private MvcResult hacer(MockHttpServletRequestBuilder b, UsuarioPrueba u, Object cuerpo) throws Exception {
        b = desdeIp(b, IP).header("Authorization", u.bearer());
        if (cuerpo != null) {
            b = b.contentType(MediaType.APPLICATION_JSON).content(json(cuerpo));
        }
        return mockMvc.perform(b).andReturn();
    }

    @Test
    @DisplayName("Sedes vía HTTP: CREAR_SEDE, EDITAR_SEDE, DESACTIVAR_SEDE y ACTIVAR_SEDE con usuario, rol, ip de la petición y detalle exacto")
    void sedesViaHttp() throws Exception {
        UsuarioPrueba admin = adminGeneral();
        String t = tag();
        Map<String, Object> alta = new HashMap<>();
        alta.put("nombre", " Sede Audit " + t + " ");
        alta.put("direccion", " Calle Audit 1 ");
        alta.put("sillones", 1);
        MvcResult r = hacer(post("/sedes"), admin, alta);
        assertThat(r.getResponse().getStatus()).isEqualTo(201);
        String id = leer(r).get("id").asText();
        assertThat(id).isEqualTo("sede-sede-audit-" + t);

        assertThat(fila("CREAR_SEDE", id)).isEqualTo(esperado(admin.id(), admin.nombre(), "ADMIN_GENERAL", null,
                "CREAR_SEDE", "sedes", id, "Sede creada: Sede Audit " + t + " | Calle Audit 1 | 1 sillón(es)", IP));

        Map<String, Object> cambio = new HashMap<>();
        cambio.put("nombre", "Sede Audit Editada " + t);
        cambio.put("direccion", "Calle Audit 2");
        cambio.put("sillones", 3);
        assertThat(hacer(put("/sedes/" + id), admin, cambio).getResponse().getStatus()).isEqualTo(200);
        assertThat(fila("EDITAR_SEDE", id)).isEqualTo(esperado(admin.id(), admin.nombre(), "ADMIN_GENERAL", null,
                "EDITAR_SEDE", "sedes", id, "Sede actualizada: Sede Audit Editada " + t + " | Calle Audit 2 | 3 sillón(es)", IP));

        assertThat(hacer(patch("/sedes/" + id + "/estado").param("activa", "false"), admin, null)
                .getResponse().getStatus()).isEqualTo(200);
        assertThat(fila("DESACTIVAR_SEDE", id)).isEqualTo(esperado(admin.id(), admin.nombre(), "ADMIN_GENERAL", null,
                "DESACTIVAR_SEDE", "sedes", id, "Sede Sede Audit Editada " + t + " marcada como INACTIVA", IP));

        assertThat(hacer(patch("/sedes/" + id + "/estado").param("activa", "true"), admin, null)
                .getResponse().getStatus()).isEqualTo(200);
        assertThat(fila("ACTIVAR_SEDE", id)).isEqualTo(esperado(admin.id(), admin.nombre(), "ADMIN_GENERAL", null,
                "ACTIVAR_SEDE", "sedes", id, "Sede Sede Audit Editada " + t + " marcada como ACTIVA", IP));
    }

    @Test
    @DisplayName("Servicios vía HTTP: CREAR/EDITAR/DESACTIVAR/ACTIVAR_SERVICIO; precio nulo 'S/ null' y precio con la escala de la columna (B-025)")
    void serviciosViaHttp() throws Exception {
        UsuarioPrueba admin = adminGeneral();
        String t = tag();
        Map<String, Object> alta = new HashMap<>();
        alta.put("nombre", "Servicio Audit " + t);
        alta.put("especialidad", "Endodoncia");
        alta.put("duracionMin", 30);
        MvcResult r = hacer(post("/servicios"), admin, alta);
        assertThat(r.getResponse().getStatus()).isEqualTo(201);
        String id = leer(r).get("id").asText();

        assertThat(fila("CREAR_SERVICIO", id)).isEqualTo(esperado(admin.id(), admin.nombre(), "ADMIN_GENERAL", null,
                "CREAR_SERVICIO", "servicios_web", id, "Servicio creado: Servicio Audit " + t + " | 30 min | S/ null", IP));

        Map<String, Object> cambio = new HashMap<>(alta);
        cambio.put("duracionMin", 45);
        // B-025: con más de 2 decimales → 400 y no se audita; con 1 decimal se audita con la escala de la columna
        cambio.put("precio", new BigDecimal("120.555"));
        assertThat(hacer(put("/servicios/" + id), admin, cambio).getResponse().getStatus()).isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria_log WHERE accion = 'EDITAR_SERVICIO' "
                + "AND registro_id = ?", Integer.class, id)).isZero();
        cambio.put("precio", new BigDecimal("120.5"));
        assertThat(hacer(put("/servicios/" + id), admin, cambio).getResponse().getStatus()).isEqualTo(200);
        assertThat(fila("EDITAR_SERVICIO", id)).isEqualTo(esperado(admin.id(), admin.nombre(), "ADMIN_GENERAL", null,
                "EDITAR_SERVICIO", "servicios_web", id,
                "Servicio actualizado: Servicio Audit " + t + " | 45 min | S/ 120.50", IP));

        assertThat(hacer(patch("/servicios/" + id + "/estado").param("activo", "false"), admin, null)
                .getResponse().getStatus()).isEqualTo(200);
        assertThat(fila("DESACTIVAR_SERVICIO", id)).isEqualTo(esperado(admin.id(), admin.nombre(), "ADMIN_GENERAL", null,
                "DESACTIVAR_SERVICIO", "servicios_web", id, "Servicio Servicio Audit " + t + " marcado como INACTIVO", IP));

        assertThat(hacer(patch("/servicios/" + id + "/estado").param("activo", "true"), admin, null)
                .getResponse().getStatus()).isEqualTo(200);
        assertThat(fila("ACTIVAR_SERVICIO", id)).isEqualTo(esperado(admin.id(), admin.nombre(), "ADMIN_GENERAL", null,
                "ACTIVAR_SERVICIO", "servicios_web", id, "Servicio Servicio Audit " + t + " marcado como ACTIVO", IP));

        // servicios_web no tiene trigger: solo las 4 filas de aplicación
        assertThat(filasDeRegistro(id)).isEqualTo(4);
    }

    @Test
    @DisplayName("Sin principal ni petición (llamada directa al servicio): 'SISTEMA' / 'SISTEMA' / sede null / 127.0.0.1")
    void sinPrincipal() {
        String t = tag();
        String idSede = sedeService.crear(SedeRequestDTO.builder().nombre("Sede Sistema " + t).direccion("Dir Sistema")
                .sillones(2).build()).getId();
        assertThat(fila("CREAR_SEDE", idSede)).isEqualTo(esperado(null, "SISTEMA", "SISTEMA", null, "CREAR_SEDE",
                "sedes", idSede, "Sede creada: Sede Sistema " + t + " | Dir Sistema | 2 sillón(es)", "127.0.0.1"));

        String idSrv = servicioService.crear(ServicioRequestDTO.builder().nombre("Servicio Sistema " + t)
                .especialidad("General").duracionMin(20).precio(new BigDecimal("15.00")).build()).getId();
        assertThat(fila("CREAR_SERVICIO", idSrv)).isEqualTo(esperado(null, "SISTEMA", "SISTEMA", null, "CREAR_SERVICIO",
                "servicios_web", idSrv, "Servicio creado: Servicio Sistema " + t + " | 20 min | S/ 15.00", "127.0.0.1"));
    }

    @Test
    @DisplayName("Con principal con sede (fuera de petición): sede_id del principal e ip 127.0.0.1")
    void principalConSede() {
        ClinicaUserPrincipal p = new ClinicaUserPrincipal(UUID.randomUUID(), "p@sedes.pe", "Dra. Principal",
                "ADMIN_SEDE", SEDE_ELTAMBO);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p, null, List.of()));
        String t = tag();
        String idSede = sedeService.crear(SedeRequestDTO.builder().nombre("Sede Principal " + t).direccion("Dir P")
                .sillones(4).build()).getId();
        sedeService.cambiarEstado(idSede, false);
        assertThat(fila("DESACTIVAR_SEDE", idSede)).isEqualTo(esperado(p.id(), "Dra. Principal", "ADMIN_SEDE",
                SEDE_ELTAMBO, "DESACTIVAR_SEDE", "sedes", idSede, "Sede Sede Principal " + t + " marcada como INACTIVA",
                "127.0.0.1"));

        String idSrv = servicioService.crear(ServicioRequestDTO.builder().nombre("Servicio Principal " + t)
                .especialidad("General").duracionMin(20).build()).getId();
        assertThat(fila("CREAR_SERVICIO", idSrv)).isEqualTo(esperado(p.id(), "Dra. Principal", "ADMIN_SEDE",
                SEDE_ELTAMBO, "CREAR_SERVICIO", "servicios_web", idSrv,
                "Servicio creado: Servicio Principal " + t + " | 20 min | S/ null", "127.0.0.1"));
    }

    @Test
    @DisplayName("Activar una sede ya activa audita ACTIVAR_SEDE aunque no cambie nada (y el trigger no escribe)")
    void activarSinCambioAudita() throws Exception {
        UsuarioPrueba admin = adminGeneral();
        String id = "sede-ya-activa-" + tag();
        insertarSede(id, "Ya Activa " + id, true, 1);
        int antes = filasDeRegistro(id);
        assertThat(hacer(patch("/sedes/" + id + "/estado").param("activa", "true"), admin, null)
                .getResponse().getStatus()).isEqualTo(200);
        assertThat(fila("ACTIVAR_SEDE", id).get("detalle")).isEqualTo("Sede Ya Activa " + id + " marcada como ACTIVA");
        assertThat(filasDeRegistro(id)).isEqualTo(antes + 1);
    }

    @Test
    @DisplayName("El trigger sedes_auditoria añade su propia fila (INSERT/UPDATE, usuario 'SISTEMA', sin rol ni ip)")
    void triggerDeSedes() throws Exception {
        UsuarioPrueba admin = adminGeneral();
        Map<String, Object> alta = new HashMap<>();
        alta.put("nombre", "Sede Trigger " + tag());
        alta.put("direccion", "Dir T");
        alta.put("sillones", 1);
        MvcResult r = hacer(post("/sedes"), admin, alta);
        String id = leer(r).get("id").asText();

        Map<String, Object> trigger = jdbc.queryForMap("SELECT usuario_id, usuario_nombre, rol, sede_id, modulo, tabla, "
                + "host(ip) AS ip, detalle FROM auditoria_log WHERE accion = 'INSERT' AND registro_id = ?", id);
        assertThat(trigger.get("usuario_id")).isNull();
        assertThat(trigger.get("usuario_nombre")).isEqualTo("SISTEMA");
        assertThat(trigger.get("rol")).isNull();
        assertThat(trigger.get("sede_id")).isNull();
        assertThat(trigger.get("modulo")).isEqualTo("CONFIGURACION");
        assertThat(trigger.get("tabla")).isEqualTo("sedes");
        assertThat(trigger.get("ip")).isNull();
        assertThat(trigger.get("detalle")).isNull();
        assertThat(filasDeRegistro(id)).isEqualTo(2); // CREAR_SEDE + INSERT del trigger
    }

    @Test
    @DisplayName("404 de sedes, 400 de servicios y 400 de validación no escriben en auditoria_log")
    void erroresNoAuditan() throws Exception {
        UsuarioPrueba admin = adminGeneral();
        String idSede = "sede-no-existe-" + tag();
        String idSrv = "srv-no-existe-" + tag();
        Map<String, Object> sede = new HashMap<>();
        sede.put("nombre", "X");
        sede.put("direccion", "Y");
        sede.put("sillones", 1);
        Map<String, Object> srv = new HashMap<>();
        srv.put("nombre", "X");
        srv.put("especialidad", "Y");
        srv.put("duracionMin", 30);

        assertThat(hacer(put("/sedes/" + idSede), admin, sede).getResponse().getStatus()).isEqualTo(404);
        assertThat(hacer(patch("/sedes/" + idSede + "/estado").param("activa", "true"), admin, null)
                .getResponse().getStatus()).isEqualTo(404);
        assertThat(hacer(put("/servicios/" + idSrv), admin, srv).getResponse().getStatus()).isEqualTo(400);
        assertThat(hacer(patch("/servicios/" + idSrv + "/estado").param("activo", "true"), admin, null)
                .getResponse().getStatus()).isEqualTo(400);
        assertThat(filasDeRegistro(idSede)).isZero();
        assertThat(filasDeRegistro(idSrv)).isZero();

        Integer antes = jdbc.queryForObject("SELECT count(*) FROM auditoria_log WHERE usuario_id = ?", Integer.class, admin.id());
        sede.put("sillones", 0);
        srv.put("duracionMin", 4);
        assertThat(hacer(post("/sedes"), admin, sede).getResponse().getStatus()).isEqualTo(400);
        assertThat(hacer(post("/servicios"), admin, srv).getResponse().getStatus()).isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria_log WHERE usuario_id = ?", Integer.class, admin.id()))
                .isEqualTo(antes).isZero();
    }
}
