package com.odontomax.identidad.caracterizacion;

import com.odontomax.agenda.application.port.out.RegistroAuditoriaPort;
import com.odontomax.application.dto.request.UsuarioCreateRequestDTO;
import com.odontomax.application.dto.response.UsuarioResponseDTO;
import com.odontomax.identidad.infrastructure.persistence.entity.UsuarioEntity;
import com.odontomax.identidad.api.ClinicaUserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Fidelidad de los escritores actuales de auditoria_log (plan, sección 9 y riesgo R2): las 10 columnas
 * guardadas, valores por defecto incluidos. Protege los pasos P1, P2 y P5.
 */
@Testcontainers(disabledWithoutDocker = true)
class AuditoriaFidelidadTest extends IdentidadPostgresTestBase {

    @Autowired
    private RegistroAuditoriaPort auditoriaAgenda;

    @Autowired
    private com.odontomax.identidad.application.port.out.RegistroAuditoriaPort authService; // auditoría de seguridad de identidad

    @Autowired
    private com.odontomax.identidad.application.port.in.GestionarUsuarioUseCase usuarioService;

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    private Map<String, Object> filaPorDetalle(String detalle) {
        List<Map<String, Object>> filas = jdbc.queryForList("SELECT usuario_id, usuario_nombre, rol, sede_id, modulo, "
                + "accion, tabla, registro_id, detalle, host(ip) AS ip FROM auditoria_log WHERE detalle = ?", detalle);
        assertThat(filas).hasSize(1);
        Map<String, Object> f = new HashMap<>(filas.get(0));
        if (f.get("usuario_id") != null) {
            f.put("usuario_id", f.get("usuario_id").toString());
        }
        return f;
    }

    private Map<String, Object> filaPorAccionYUsuario(String accion, UUID usuarioId) {
        List<Map<String, Object>> filas = jdbc.queryForList("SELECT usuario_id, usuario_nombre, rol, sede_id, modulo, "
                + "accion, tabla, registro_id, detalle, host(ip) AS ip FROM auditoria_log WHERE accion = ? "
                + "AND usuario_id = ?", accion, usuarioId);
        assertThat(filas).hasSize(1);
        Map<String, Object> f = new HashMap<>(filas.get(0));
        f.put("usuario_id", f.get("usuario_id").toString());
        return f;
    }

    private static Map<String, Object> esperado(Object usuarioId, String nombre, String rol, String sede, String modulo,
                                                String accion, String tabla, String registroId, String detalle, String ip) {
        Map<String, Object> m = new HashMap<>();
        m.put("usuario_id", usuarioId == null ? null : usuarioId.toString());
        m.put("usuario_nombre", nombre);
        m.put("rol", rol);
        m.put("sede_id", sede);
        m.put("modulo", modulo);
        m.put("accion", accion);
        m.put("tabla", tabla);
        m.put("registro_id", registroId);
        m.put("detalle", detalle);
        m.put("ip", ip);
        return m;
    }

    private void autenticar(ClinicaUserPrincipal p) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p, null, List.of()));
    }

    @Test
    @DisplayName("Agenda, variante de 4 args: sin rol, tabla, registro_id ni ip; nombre por defecto 'SISTEMA'")
    void agendaCuatroArgs() {
        ClinicaUserPrincipal p = new ClinicaUserPrincipal(UUID.randomUUID(), "x@y.pe", "Dra. Agenda", "RECEPCION", SEDE);
        autenticar(p);
        String d1 = "agenda4-" + UUID.randomUUID();
        auditoriaAgenda.registrar("CREAR_CITA", "AGENDA", "sede-eltambo", d1);
        assertThat(filaPorDetalle(d1)).isEqualTo(
                esperado(p.id(), "Dra. Agenda", null, "sede-eltambo", "AGENDA", "CREAR_CITA", null, null, d1, null));

        SecurityContextHolder.clearContext();
        String d2 = "agenda4-anon-" + UUID.randomUUID();
        auditoriaAgenda.registrar("CREAR_CITA", "AGENDA", null, d2);
        assertThat(filaPorDetalle(d2)).isEqualTo(
                esperado(null, "SISTEMA", null, null, "AGENDA", "CREAR_CITA", null, null, d2, null));
    }

    @Test
    @DisplayName("Agenda, variante de 5 args: sin rol, sede ni ip; nombre por defecto 'SISTEMA'")
    void agendaCincoArgs() {
        ClinicaUserPrincipal p = new ClinicaUserPrincipal(UUID.randomUUID(), "x@y.pe", "Dr. Cinco", "ODONTOLOGO", SEDE);
        autenticar(p);
        String d1 = "agenda5-" + UUID.randomUUID();
        auditoriaAgenda.registrar("AGENDA", "ELIMINAR", "especialistas", "reg-1", d1);
        assertThat(filaPorDetalle(d1)).isEqualTo(
                esperado(p.id(), "Dr. Cinco", null, null, "AGENDA", "ELIMINAR", "especialistas", "reg-1", d1, null));

        SecurityContextHolder.clearContext();
        String d2 = "agenda5-anon-" + UUID.randomUUID();
        auditoriaAgenda.registrar("AGENDA", "ELIMINAR", "especialistas", "reg-2", d2);
        assertThat(filaPorDetalle(d2)).isEqualTo(
                esperado(null, "SISTEMA", null, null, "AGENDA", "ELIMINAR", "especialistas", "reg-2", d2, null));
    }

    @Test
    @DisplayName("AuthService con usuario (CAMBIO_PASSWORD vía HTTP): modulo SEGURIDAD, tabla usuarios, ip de la petición")
    void authServiceConUsuario() throws Exception {
        UsuarioEntity u = crearUsuario("ADMIN_SEDE", PASSWORD);
        String token = tokenDe(u, PASSWORD);
        mockMvc.perform(desdeIp(post("/auth/cambiar-password"), "10.20.30.40").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("password_actual", PASSWORD, "password_nuevo", "Nueva-Audit-1"))));

        assertThat(filaPorAccionYUsuario("CAMBIO_PASSWORD", u.getId())).isEqualTo(esperado(u.getId(), u.getNombre(),
                "ADMIN_SEDE", SEDE, "SEGURIDAD", "CAMBIO_PASSWORD", "usuarios", null,
                "Cambio de contraseña exitoso cumpliendo política de complejidad e historial", "10.20.30.40"));
    }

    @Test
    @DisplayName("AuthService con usuario null (solo por reflexión): 'Sistema' / 'ANONYMOUS' / ip 127.0.0.1 fuera de petición")
    void authServiceUsuarioNull() {
        String d = "auth-null-" + UUID.randomUUID();
        EstadoEnMemoriaTestSupport.registrarAuditoriaAuthService(authService, null, "PRUEBA_NULL", d);
        assertThat(filaPorDetalle(d)).isEqualTo(
                esperado(null, "Sistema", "ANONYMOUS", null, "SEGURIDAD", "PRUEBA_NULL", "usuarios", null, d, "127.0.0.1"));
    }

    @Test
    @DisplayName("UsuarioService con principal e ip: CREAR_USUARIO con registro_id = id creado")
    void usuarioServiceConPrincipal() {
        ClinicaUserPrincipal admin = new ClinicaUserPrincipal(UUID.randomUUID(), "a@b.pe", "Admin Audit", "ADMIN_GENERAL", null);
        String email = emailUnico("audit");
        UsuarioResponseDTO creado = usuarioService.crearUsuario(UsuarioCreateRequestDTO.builder().nombre("Recepcion Audit")
                .email(email).password("Clave-1234").rol("RECEPCION").sedeId(SEDE).build(), admin, "10.9.8.7");

        String detalle = "Usuario creado: " + email + " | Rol: RECEPCION | Sede: " + SEDE;
        assertThat(filaPorDetalle(detalle)).isEqualTo(esperado(admin.id(), "Admin Audit", "ADMIN_GENERAL", null,
                "USUARIOS", "CREAR_USUARIO", "usuarios", creado.getId().toString(), detalle, "10.9.8.7"));
    }

    @Test
    @DisplayName("UsuarioService con principal null e ip null: 'SISTEMA' / 'ADMIN_GENERAL' / '127.0.0.1'")
    void usuarioServicePrincipalNull() {
        String email = emailUnico("audit-null");
        UsuarioResponseDTO creado = usuarioService.crearUsuario(UsuarioCreateRequestDTO.builder().nombre("Recepcion Null")
                .email(email).password("Clave-1234").rol("RECEPCION").sedeId(SEDE).build(), null, null);

        String detalle = "Usuario creado: " + email + " | Rol: RECEPCION | Sede: " + SEDE;
        assertThat(filaPorDetalle(detalle)).isEqualTo(esperado(null, "SISTEMA", "ADMIN_GENERAL", null,
                "USUARIOS", "CREAR_USUARIO", "usuarios", creado.getId().toString(), detalle, "127.0.0.1"));
    }

    @Test
    @DisplayName("El login (exitoso o fallido) NO escribe en auditoria_log")
    void loginNoSeAudita() throws Exception {
        UsuarioEntity u = crearUsuario("RECEPCION", PASSWORD);
        login(u.getEmail(), "mala", ipUnica());
        login(u.getEmail(), PASSWORD, ipUnica());
        Integer filas = jdbc.queryForObject("SELECT count(*) FROM auditoria_log WHERE usuario_id = ?", Integer.class, u.getId());
        assertThat(filas).isZero();
    }
}
