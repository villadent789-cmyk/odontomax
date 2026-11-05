package com.odontomax.pacientes.caracterizacion;

import com.odontomax.pacientes.application.port.in.GestionarAntecedentesUseCase;
import com.odontomax.pacientes.application.port.in.GestionarConsentimientosUseCase;
import com.odontomax.pacientes.application.port.in.GestionarAccesoHistoriaUseCase;
import com.odontomax.pacientes.application.port.in.EliminarPacienteUseCase;
import com.odontomax.pacientes.application.dto.request.AntecedentesUpdateRequestDTO;
import com.odontomax.pacientes.application.dto.request.ConsentimientoRequestDTO;
import com.odontomax.identidad.api.ClinicaUserPrincipal;
import com.odontomax.pacientes.application.port.in.ExportarExpedienteUseCase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Fidelidad de la auditoria del modulo pacientes (plan, seccion 6; paso P5): las 10 columnas de auditoria_log que
 * escriben ELIMINAR_PACIENTE, ACTUALIZAR_ANTECEDENTES, REGISTRAR_CONSENTIMIENTO, APERTURA_HISTORIA,
 * LECTURA_EXPEDIENTE y EXPORTAR_ARCO, con valores exactos. Las filas se buscan por {@code accion} de aplicacion
 * porque las tablas pacientes y consentimientos tienen ademas triggers (INSERT/UPDATE/DELETE, usuario 'SISTEMA').
 *
 * Los servicios se invocan por tipo cuando hace falta un principal nulo o una IP concreta; esos imports cambian en
 * los pasos que los muevan (P4, P8). ELIMINAR_PACIENTE solo es observable con el esquema parcheado de B-044.
 */
@Testcontainers(disabledWithoutDocker = true)
@ExtendWith(OutputCaptureExtension.class)
class PacientesAuditoriaFidelidadTest extends PacientesPostgresTestBase {

    private static final String IP = "10.77.5.9";

    @Autowired
    private EliminarPacienteUseCase pacienteService;

    @Autowired
    private GestionarAntecedentesUseCase antecedentesService;

    @Autowired
    private GestionarConsentimientosUseCase consentimientoService;

    @Autowired
    private GestionarAccesoHistoriaUseCase accesoService;

    @Autowired
    private ExportarExpedienteUseCase exportacionService;

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    private static void autenticarComo(ClinicaUserPrincipal p) {
        SecurityContextHolder.getContext().setAuthentication(new UsuarioToken(p));
    }

    /** Autenticacion minima con el principal de la aplicacion. */
    private static final class UsuarioToken extends UsernamePasswordAuthenticationToken {
        UsuarioToken(ClinicaUserPrincipal p) {
            super(p, null, List.of());
        }
    }

    private static ClinicaUserPrincipal principalDe(UsuarioPrueba u) {
        return new ClinicaUserPrincipal(u.id(), u.email(), u.nombre(), u.rol(), u.sedeId());
    }

    private MvcResult eliminar(UsuarioPrueba u, UUID id, String motivo) throws Exception {
        var b = delete("/pacientes/" + id).param("confirmacion", "true");
        if (motivo != null) {
            b = b.param("motivo", motivo);
        }
        return ejecutar(b, u, IP, null);
    }

    // ------------------------------------------------------------------ ELIMINAR_PACIENTE

    @Test
    @DisplayName("ELIMINAR_PACIENTE por HTTP: usuario, rol, sede del principal (null para ADMIN_GENERAL), ip de la peticion y detalle exacto con el motivo normalizado")
    void eliminarViaHttp() throws Exception {
        String sede = crearSede();
        UsuarioPrueba admin = adminGeneral();
        UUID id = insertarPaciente(sede, "Ana", "Pérez");
        String dni = (String) pacienteBd(id).get("dni");

        MvcResult r = eliminar(admin, id, "  Cambio   de\n ciudad  ");
        assertThat(r.getResponse().getStatus()).isEqualTo(200);

        assertThat(filaAuditoria("ELIMINAR_PACIENTE", id.toString())).isEqualTo(esperadoAuditoria(admin.id(), admin.nombre(),
                "ADMIN_GENERAL", null, "PACIENTES", "ELIMINAR_PACIENTE", "pacientes", id.toString(),
                "Paciente eliminado permanentemente de Supabase y Cloudflare R2: Ana Pérez (DNI: " + dni + ", Sede: " + sede
                        + ") | Motivo: Cambio de ciudad", IP));
        // el trigger de pacientes anade su propia fila DELETE (usuario SISTEMA, modulo HISTORIA_CLINICA)
        List<Map<String, Object>> triggers = jdbc.queryForList("SELECT usuario_nombre, modulo, rol, host(ip) AS ip FROM auditoria_log "
                + "WHERE accion = 'DELETE' AND tabla = 'pacientes' AND registro_id = ?", id.toString());
        assertThat(triggers).hasSize(1);
        assertThat(triggers.get(0).get("usuario_nombre")).isEqualTo("SISTEMA");
        assertThat(triggers.get(0).get("modulo")).isEqualTo("HISTORIA_CLINICA");
        assertThat(triggers.get(0).get("rol")).isNull();
        assertThat(triggers.get(0).get("ip")).isNull();
    }

    @Test
    @DisplayName("Motivo de eliminacion: sin motivo o en blanco 'no indicado'; se colapsan espacios y se corta a 500 caracteres")
    void eliminarMotivos() throws Exception {
        String sede = crearSede();
        UsuarioPrueba admin = adminGeneral();
        String[][] casos = {
                {null, "no indicado"},
                {"", "no indicado"},
                {"     ", "no indicado"},
                {"\t Solicitud\t\tdel   paciente \n", "Solicitud del paciente"},
                {"a".repeat(600), "a".repeat(500)},
                {"  " + "b".repeat(498) + "   cc  ", "b".repeat(498) + " c"},
        };
        for (String[] caso : casos) {
            UUID id = insertarPaciente(sede, "Mot", "Ivo");
            assertThat(eliminar(admin, id, caso[0]).getResponse().getStatus()).isEqualTo(200);
            String detalle = (String) filaAuditoria("ELIMINAR_PACIENTE", id.toString()).get("detalle");
            assertThat(detalle).endsWith(" | Motivo: " + caso[1]);
        }
    }

    @Test
    @DisplayName("ELIMINAR_PACIENTE con principal nulo: usuario null, 'SISTEMA', rol 'ADMIN_GENERAL', sede null e ip '127.0.0.1'")
    void eliminarSinPrincipal() throws Exception {
        String sede = crearSede();
        UUID id = insertarPaciente(sede, "Sin", "Principal");
        autenticarComo(new ClinicaUserPrincipal(UUID.randomUUID(), "contexto@x.pe", "Contexto", "ADMIN_GENERAL", null));

        pacienteService.eliminar(id, null, null, null);

        Map<String, Object> fila = filaAuditoria("ELIMINAR_PACIENTE", id.toString());
        assertThat(fila.get("usuario_id")).isNull();
        assertThat(fila.get("usuario_nombre")).isEqualTo("SISTEMA");
        assertThat(fila.get("rol")).isEqualTo("ADMIN_GENERAL");
        assertThat(fila.get("sede_id")).isNull();
        assertThat(fila.get("modulo")).isEqualTo("PACIENTES");
        assertThat(fila.get("tabla")).isEqualTo("pacientes");
        assertThat(fila.get("ip")).isEqualTo("127.0.0.1");
        assertThat((String) fila.get("detalle")).startsWith("Paciente eliminado permanentemente de Supabase y Cloudflare R2: Sin Principal (DNI: ")
                .endsWith(", Sede: " + sede + ") | Motivo: no indicado");
    }

    @Test
    @DisplayName("ELIMINAR_PACIENTE con principal con sede: la sede es la del PRINCIPAL (no la del paciente) y la ip la recibida")
    void eliminarConPrincipalConSede() throws Exception {
        String sede = crearSede();
        UUID id = insertarPaciente(sede, "Con", "PrincipalSede");
        ClinicaUserPrincipal principal = new ClinicaUserPrincipal(UUID.randomUUID(), "ad@x.pe", "Admin Sede", "ADMIN_SEDE", sede);
        autenticarComo(principal);

        pacienteService.eliminar(id, principal, "10.1.2.3", "motivo");

        Map<String, Object> fila = filaAuditoria("ELIMINAR_PACIENTE", id.toString());
        assertThat(fila.get("usuario_id")).isEqualTo(principal.id().toString());
        assertThat(fila.get("usuario_nombre")).isEqualTo("Admin Sede");
        assertThat(fila.get("rol")).isEqualTo("ADMIN_SEDE");
        assertThat(fila.get("sede_id")).isEqualTo(sede);
        assertThat(fila.get("ip")).isEqualTo("10.1.2.3");
    }

    // ------------------------------------------------------------------ ACTUALIZAR_ANTECEDENTES

    private Map<String, Object> antecedentes(String motivo) {
        Map<String, Object> m = new HashMap<>();
        m.put("alergias", "Penicilina");
        m.put("antecedentesMedicos", "Asma");
        m.put("gestante", false);
        m.put("motivoCambio", motivo);
        return m;
    }

    @Test
    @DisplayName("ACTUALIZAR_ANTECEDENTES: usuario y rol del solicitante, sede del PACIENTE (tambien para ADMIN_GENERAL), ip de la peticion; el detalle lleva el motivo sin recortar")
    void actualizarAntecedentes() throws Exception {
        String sede = crearSede();
        for (String rol : List.of("ODONTOLOGO", "ADMIN_SEDE", "ADMIN_GENERAL")) {
            UsuarioPrueba u = crearUsuario(rol, "ADMIN_GENERAL".equals(rol) ? null : sede);
            UUID id = insertarPaciente(sede, "Antec", rol);
            if ("ODONTOLOGO".equals(rol)) vincularCita(id, u);
            MvcResult r = ejecutar(put("/antecedentes/pacientes/" + id), u, IP, antecedentes("  Control anual  "));
            assertThat(r.getResponse().getStatus()).as(rol).isEqualTo(200);

            assertThat(filaAuditoria("ACTUALIZAR_ANTECEDENTES", id.toString())).as(rol).isEqualTo(esperadoAuditoria(u.id(),
                    u.nombre(), rol, sede, "HISTORIA_CLINICA", "ACTUALIZAR_ANTECEDENTES", "antecedentes_historial",
                    id.toString(), "Actualización de alergias y antecedentes:   Control anual  ", IP));
            // la instantanea inmutable guarda el motivo recortado
            assertThat(jdbc.queryForObject("SELECT motivo_cambio FROM antecedentes_historial WHERE paciente_id = ?", String.class, id))
                    .isEqualTo("Control anual");
        }
    }

    @Test
    @DisplayName("Los valores de respaldo 'Sistema' / 'ANONYMOUS' son inalcanzables: sin principal autenticado los servicios lanzan AccessDeniedException ANTES de auditar")
    void valoresDeRespaldoInalcanzables() throws Exception {
        String sede = crearSede();
        UUID id = insertarPaciente(sede, "Sin", "Autenticar");
        SecurityContextHolder.clearContext();

        ConsentimientoRequestDTO consentimiento = new ConsentimientoRequestDTO();
        consentimiento.setPacienteId(id);
        consentimiento.setTipo("USO_IMAGEN");
        consentimiento.setContenidoAceptado("x");
        consentimiento.setFirmaBase64(PNG_BASE64);
        consentimiento.setNombreFirmante("n");
        consentimiento.setDocumentoFirmante("1");
        AntecedentesUpdateRequestDTO ant = new AntecedentesUpdateRequestDTO();
        ant.setMotivoCambio("m");

        assertThatThrownBy(() -> antecedentesService.actualizarAntecedentes(id, ant))
                .isInstanceOf(AccessDeniedException.class).hasMessage("No se encuentra autenticado en el sistema.");
        assertThatThrownBy(() -> consentimientoService.registrarConsentimiento(consentimiento))
                .isInstanceOf(AccessDeniedException.class).hasMessage("No se encuentra autenticado en el sistema.");
        assertThatThrownBy(() -> accesoService.registrarApertura(id))
                .isInstanceOf(AccessDeniedException.class).hasMessage("No se encuentra autenticado en el sistema.");
        assertThatThrownBy(() -> exportacionService.exportarExpedienteCompleto(id))
                .isInstanceOf(AccessDeniedException.class).hasMessage("No se encuentra autenticado en el sistema.");
        assertThat(contar("SELECT count(*) FROM auditoria_log WHERE registro_id = ? AND accion IN ('ACTUALIZAR_ANTECEDENTES', "
                + "'REGISTRAR_CONSENTIMIENTO', 'APERTURA_HISTORIA', 'EXPORTAR_ARCO')", id.toString())).isZero();
        assertThat(contar("SELECT count(*) FROM historia_clinica_accesos WHERE paciente_id = ?", id)).isZero();
    }

    // ------------------------------------------------------------------ REGISTRAR_CONSENTIMIENTO

    @Test
    @DisplayName("REGISTRAR_CONSENTIMIENTO: registro_id = id del consentimiento, sede del paciente, ip, detalle con tipo normalizado, firmante y DOCUMENTO (B-052); el trigger anade la fila INSERT")
    void registrarConsentimiento() throws Exception {
        String sede = crearSede();
        UsuarioPrueba recepcion = crearUsuario("RECEPCION", sede);
        UUID id = insertarPaciente(sede, "Consen", "Timiento");
        Map<String, Object> body = new HashMap<>();
        body.put("pacienteId", id.toString());
        body.put("tipo", " uso_imagen ");
        body.put("contenidoAceptado", "Acepto");
        body.put("firmaBase64", PNG_BASE64);
        body.put("nombreFirmante", "  Maria Lopez  ");
        body.put("documentoFirmante", " 12345678 ");

        MvcResult r = ejecutar(post("/consentimientos"), recepcion, IP, body);
        assertThat(r.getResponse().getStatus()).isEqualTo(201);
        String consentimientoId = leer(r).get("id").asText();

        assertThat(filaAuditoria("REGISTRAR_CONSENTIMIENTO", consentimientoId)).isEqualTo(esperadoAuditoria(recepcion.id(),
                recepcion.nombre(), "RECEPCION", sede, "HISTORIA_CLINICA", "REGISTRAR_CONSENTIMIENTO", "consentimientos",
                consentimientoId, "Consentimiento 'USO_IMAGEN' firmado por Maria Lopez (DNI: 12345678)", IP));
        List<Map<String, Object>> trigger = jdbc.queryForList("SELECT usuario_nombre, modulo, rol FROM auditoria_log "
                + "WHERE accion = 'INSERT' AND tabla = 'consentimientos' AND registro_id = ?", consentimientoId);
        assertThat(trigger).hasSize(1);
        assertThat(trigger.get(0).get("usuario_nombre")).isEqualTo("SISTEMA");
        assertThat(trigger.get(0).get("modulo")).isEqualTo("HISTORIA_CLINICA");
    }

    // ------------------------------------------------------------------ APERTURA_HISTORIA / LECTURA_EXPEDIENTE

    @Test
    @DisplayName("APERTURA_HISTORIA: usuario, rol, sede y detalle exactos; registro_id = id del paciente; una segunda apertura del mismo usuario no audita; otro usuario si")
    void aperturaHistoria() throws Exception {
        String sede = crearSede();
        UsuarioPrueba odontologo = crearUsuario("ODONTOLOGO", sede);
        UsuarioPrueba admin = adminGeneral();
        UUID id = insertarPaciente(sede, "Abre", "Historia");
        vincularCita(id, odontologo);

        assertThat(ejecutar(post("/historia-clinica/pacientes/" + id + "/apertura"), odontologo, IP, null)
                .getResponse().getStatus()).isEqualTo(201);

        assertThat(filaAuditoria("APERTURA_HISTORIA", id.toString())).isEqualTo(esperadoAuditoria(odontologo.id(),
                odontologo.nombre(), "ODONTOLOGO", sede, "HISTORIA_CLINICA", "APERTURA_HISTORIA", "historia_clinica_accesos",
                id.toString(), "Apertura de expediente e historia clínica del paciente Abre Historia", IP));

        // repetida dentro de los 5 minutos: devuelve el mismo acceso y no audita
        assertThat(ejecutar(post("/historia-clinica/pacientes/" + id + "/apertura"), odontologo, IP, null)
                .getResponse().getStatus()).isEqualTo(201);
        assertThat(filasAuditoria("APERTURA_HISTORIA", id.toString())).hasSize(1);

        // otro usuario (ADMIN_GENERAL, sede nula): sede_id de la auditoria = sede DEL PACIENTE
        assertThat(ejecutar(post("/historia-clinica/pacientes/" + id + "/apertura"), admin, "10.0.0.8", null)
                .getResponse().getStatus()).isEqualTo(201);
        List<Map<String, Object>> filas = filasAuditoria("APERTURA_HISTORIA", id.toString());
        assertThat(filas).hasSize(2);
        assertThat(filas.get(1)).isEqualTo(esperadoAuditoria(admin.id(), admin.nombre(), "ADMIN_GENERAL", sede,
                "HISTORIA_CLINICA", "APERTURA_HISTORIA", "historia_clinica_accesos", id.toString(),
                "Apertura de expediente e historia clínica del paciente Abre Historia", "10.0.0.8"));
    }

    @Test
    @DisplayName("LECTURA_EXPEDIENTE (sin llamadores, B-048): ip por defecto '127.0.0.1' en acceso y auditoria, deduplicada 5 minutos; sin usuario, sin paciente o paciente inexistente no hace nada")
    void lecturaExpedienteDeduplicada() throws Exception {
        String sede = crearSede();
        UsuarioPrueba u = crearUsuario("ADMIN_SEDE", sede);
        UUID id = insertarPaciente(sede, "Lee", "Expediente");
        UUID otro = insertarPaciente(sede, "Lee", "Otro");

        accesoService.registrarLecturaDeduplicada(id, principalDe(u), null);
        accesoService.registrarLecturaDeduplicada(id, principalDe(u), "10.9.9.9");
        accesoService.registrarLecturaDeduplicada(null, principalDe(u), null);
        accesoService.registrarLecturaDeduplicada(id, null, null);
        accesoService.registrarLecturaDeduplicada(UUID.randomUUID(), principalDe(u), null);

        assertThat(filaAuditoria("LECTURA_EXPEDIENTE", id.toString())).isEqualTo(esperadoAuditoria(u.id(), u.nombre(),
                "ADMIN_SEDE", sede, "HISTORIA_CLINICA", "LECTURA_EXPEDIENTE", "historia_clinica_accesos", id.toString(),
                "Lectura de expediente clínico del paciente Lee Expediente", "127.0.0.1"));
        assertThat(jdbc.queryForObject("SELECT ip FROM historia_clinica_accesos WHERE paciente_id = ?", String.class, id))
                .isEqualTo("127.0.0.1");
        assertThat(contar("SELECT count(*) FROM historia_clinica_accesos WHERE paciente_id = ?", id)).isEqualTo(1);

        accesoService.registrarLecturaDeduplicada(otro, principalDe(u), "10.9.9.9");
        assertThat(filaAuditoria("LECTURA_EXPEDIENTE", otro.toString()).get("ip")).isEqualTo("10.9.9.9");
    }

    // ------------------------------------------------------------------ EXPORTAR_ARCO

    @Test
    @DisplayName("EXPORTAR_ARCO: usuario, rol, sede del paciente, tabla 'pacientes', ip y detalle exactos (ADMIN_SEDE y ADMIN_GENERAL)")
    void exportarArco() throws Exception {
        String sede = crearSede();
        for (String rol : List.of("ADMIN_SEDE", "ADMIN_GENERAL")) {
            UsuarioPrueba u = crearUsuario(rol, "ADMIN_GENERAL".equals(rol) ? null : sede);
            UUID id = insertarPaciente(sede, "Exporta", rol);
            MvcResult r = ejecutar(get("/pacientes/" + id + "/exportar"), u, IP, null);
            assertThat(r.getResponse().getStatus()).as(rol).isEqualTo(200);

            assertThat(filaAuditoria("EXPORTAR_ARCO", id.toString())).as(rol).isEqualTo(esperadoAuditoria(u.id(), u.nombre(), rol,
                    sede, "HISTORIA_CLINICA", "EXPORTAR_ARCO", "pacientes", id.toString(),
                    "Exportación integral de expediente y datos del paciente Exporta " + rol
                            + " conforme a la Ley N° 29733", IP));
        }
    }

    // ------------------------------------------------------------------ archivar (B-047) y logs (B-050)

    @Test
    @DisplayName("B-047: archivar NO deja auditoria de aplicacion; solo el trigger de pacientes (UPDATE, usuario 'SISTEMA', sin rol ni ip) y no se sabe quien archivo")
    void b047ArchivarNoAudita() throws Exception {
        String sede = crearSede();
        UsuarioPrueba admin = crearUsuario("ADMIN_SEDE", sede);
        UUID id = insertarPaciente(sede, "Archiva", "SinAuditoria");
        Map<String, Object> body = new HashMap<>();
        body.put("motivo", "Se mudo de ciudad");

        assertThat(ejecutar(post("/pacientes/" + id + "/archivar"), admin, IP, body).getResponse().getStatus()).isEqualTo(200);

        List<Map<String, Object>> filas = jdbc.queryForList("SELECT accion, usuario_id, usuario_nombre, rol, sede_id, modulo, "
                + "tabla, host(ip) AS ip, detalle, datos_antes->>'archivado' AS antes, datos_despues->>'archivado' AS despues, "
                + "datos_despues->>'motivo_archivo' AS motivo FROM auditoria_log WHERE registro_id = ? ORDER BY id", id.toString());
        assertThat(filas).extracting(f -> f.get("accion")).containsExactly("INSERT", "UPDATE");
        Map<String, Object> update = filas.get(1);
        assertThat(update.get("usuario_id")).isNull();
        assertThat(update.get("usuario_nombre")).isEqualTo("SISTEMA");
        assertThat(update.get("rol")).isNull();
        assertThat(update.get("sede_id")).isNull();
        assertThat(update.get("modulo")).isEqualTo("HISTORIA_CLINICA");
        assertThat(update.get("tabla")).isEqualTo("pacientes");
        assertThat(update.get("ip")).isNull();
        assertThat(update.get("detalle")).isNull();
        assertThat(update.get("antes")).isEqualTo("false");
        assertThat(update.get("despues")).isEqualTo("true");
        assertThat(update.get("motivo")).isEqualTo("Se mudo de ciudad");
    }

    @Test
    @DisplayName("B-050 (datos personales): una violacion UNIQUE (DNI repetido) se registra en el log WARN con el DNI incluido en la causa SQL")
    void b050DniEnElLog(CapturedOutput salida) throws Exception {
        String sede = crearSede();
        UsuarioPrueba admin = adminGeneral();
        Map<String, Object> primero = cuerpoPaciente(sede);
        assertThat(ejecutar(post("/pacientes"), admin, primero).getResponse().getStatus()).isEqualTo(201);
        Map<String, Object> repetido = cuerpoPaciente(sede);
        repetido.put("dni", primero.get("dni"));

        assertThat(ejecutar(post("/pacientes"), admin, repetido).getResponse().getStatus()).isEqualTo(409);

        assertThat(salida.getAll()).contains("de integridad de datos")
                .contains("Key (tipo_documento, dni, unidad_de_negocio)=(DNI, " + primero.get("dni") + ", VILLADENT) already exists");
    }
}
