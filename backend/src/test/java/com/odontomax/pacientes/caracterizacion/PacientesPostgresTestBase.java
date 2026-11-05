package com.odontomax.pacientes.caracterizacion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.odontomax.domain.port.out.StoragePort;
import com.odontomax.identidad.infrastructure.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Base de la caracterizacion del modulo pacientes (Fase 3, paso P0).
 *
 * Contexto completo de Spring Boot (cadena de filtros real, @PreAuthorize, servicios reales, Flyway) contra
 * PostgreSQL en Testcontainers. El contenedor es unico para todas las subclases (patron singleton) y, como todas
 * comparten configuracion, tambien el contexto de Spring en cache.
 *
 * Los usuarios se insertan por SQL y los JWT se emiten con el {@link JwtTokenProvider} real. Cada test crea sus
 * propias sedes y pacientes (nombres, DNI y sedes unicos): la base de datos es compartida y las tablas de historia
 * clinica son inmutables (no se pueden limpiar).
 *
 * El almacenamiento (R2) es un {@code @SpyBean} sobre el adaptador real: sin credenciales devuelve claves y URLs
 * simuladas, y los tests pueden verificar las llamadas o forzar fallos.
 */
@SpringBootTest
@AutoConfigureMockMvc
abstract class PacientesPostgresTestBase {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("pacientes_caract")
            .withUsername("testuser")
            .withPassword("testpass");

    static {
        if (DockerClientFactory.instance().isDockerAvailable()) {
            POSTGRES.start();
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        if (POSTGRES.isRunning()) {
            registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
            registry.add("spring.datasource.username", POSTGRES::getUsername);
            registry.add("spring.datasource.password", POSTGRES::getPassword);
            registry.add("spring.flyway.enabled", () -> "true");
            registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
            registry.add("spring.flyway.user", POSTGRES::getUsername);
            registry.add("spring.flyway.password", POSTGRES::getPassword);
            registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        }
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected JwtTokenProvider jwtTokenProvider;

    @SpyBean
    protected StoragePort storagePort;

    private static final Random RANDOM = new Random();
    private static final AtomicInteger DNI = new AtomicInteger(10_000_000 + new Random().nextInt(60_000_000));

    /** Usuario insertado en BD con su JWT de sesion real (token_version 0). */
    public record UsuarioPrueba(UUID id, String email, String nombre, String rol, String sedeId, String token) {
        public String bearer() {
            return "Bearer " + token;
        }
    }

    // ---------------------------------------------------------------- fixtures

    /** Sufijo aleatorio de 6 letras minusculas: sobrevive intacto al slug de los ids. */
    protected static String tag() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            sb.append((char) ('a' + RANDOM.nextInt(26)));
        }
        return sb.toString();
    }

    /** DNI de 8 digitos unico en toda la ejecucion. */
    protected static String dniUnico() {
        return String.valueOf(DNI.incrementAndGet());
    }

    protected String crearSede() {
        String id = "sede-pac-" + tag();
        jdbc.update("INSERT INTO sedes (id, nombre, direccion, sillones, activa) VALUES (?, ?, ?, ?, true)",
                id, "Sede " + id, "Direccion " + id, 3);
        return id;
    }

    protected UsuarioPrueba crearUsuario(String rol, String sedeId) {
        UUID id = UUID.randomUUID();
        String email = rol.toLowerCase().replace('_', '-') + "-" + id.toString().substring(0, 8) + "@pacientes.odontomax.pe";
        String nombre = "Usuario " + rol + " " + id.toString().substring(0, 4);
        jdbc.update("INSERT INTO usuarios (id, nombre, email, rol, sede_id, activo) VALUES (?, ?, ?, ?, ?, true)",
                id, nombre, email, rol, sedeId);
        if ("ODONTOLOGO".equals(rol)) {
            Integer count = jdbc.queryForObject("SELECT count(*) FROM especialistas WHERE usuario_id = ?", Integer.class, id);
            if (count == null || count == 0) {
                UUID espId = UUID.randomUUID();
                jdbc.update("INSERT INTO especialistas (id, usuario_id, nombre, especialidad, activo) VALUES (?, ?, ?, 'General', true)",
                        espId, id, nombre);
                if (sedeId != null) {
                    jdbc.update("INSERT INTO especialista_sedes (especialista_id, sede_id) VALUES (?, ?)", espId, sedeId);
                }
            }
        }
        String token = jwtTokenProvider.generateToken(id, email, nombre, rol, sedeId, 0);
        return new UsuarioPrueba(id, email, nombre, rol, sedeId, token);
    }

    private static final java.util.concurrent.atomic.AtomicInteger CITA_DIA_OFFSET = new java.util.concurrent.atomic.AtomicInteger(1);

    protected void vincularCita(UUID pacienteId, UsuarioPrueba odontologo) {
        UUID espId = jdbc.queryForObject("SELECT id FROM especialistas WHERE usuario_id = ?", UUID.class, odontologo.id());
        UUID citaId = UUID.randomUUID();
        String sede = odontologo.sedeId() != null ? odontologo.sedeId() : "sede-chilca-1";
        LocalDate fecha = LocalDate.now().minusDays(CITA_DIA_OFFSET.incrementAndGet());
        jdbc.update("INSERT INTO citas (id, paciente_id, paciente_nombre, doctor_id, sede_id, motivo_consulta, fecha, hora, duracion_minutos, estado, adelanto_requerido, adelanto_monto, adelanto_estado, estado_confirmacion) " +
                "VALUES (?, ?, 'Paciente', ?, ?, 'Control', ?, '10:00', 30, 'PROGRAMADA', false, 0, 'NO_APLICA', 'SIN_CONTACTAR')",
                citaId, pacienteId, espId, sede, fecha);
    }

    protected UsuarioPrueba adminGeneral() {
        return crearUsuario("ADMIN_GENERAL", null);
    }

    /** Inserta un paciente por SQL (activo, sin saldo) y devuelve su id. */
    protected UUID insertarPaciente(String sedeId, String nombres, String apellidos) {
        return insertarPaciente(sedeId, dniUnico(), nombres, apellidos);
    }

    protected UUID insertarPaciente(String sedeId, String dni, String nombres, String apellidos) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO pacientes (id, dni, nombres, apellidos, sede_id) VALUES (?, ?, ?, ?, ?)",
                id, dni, nombres, apellidos, sedeId);
        return id;
    }

    protected Map<String, Object> pacienteBd(UUID id) {
        return jdbc.queryForMap("SELECT * FROM pacientes WHERE id = ?", id);
    }

    protected boolean existePaciente(UUID id) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM pacientes WHERE id = ?", Integer.class, id);
        return n != null && n > 0;
    }

    protected int contar(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    protected UUID insertarCita(UUID pacienteId, String sedeId, String estado, LocalDate fecha) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO citas (id, paciente_id, sede_id, motivo_consulta, fecha, hora, estado) "
                + "VALUES (?, ?, ?, 'Consulta de prueba', ?, '10:00', ?)", id, pacienteId, sedeId, fecha, estado);
        return id;
    }

    protected UUID insertarTratamiento(UUID pacienteId, String sedeId, String nombre, String costo) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO tratamientos (id, paciente_id, sede_id, nombre, costo_total, estado) "
                + "VALUES (?, ?, ?, ?, ?::numeric, 'EN_PROCESO')", id, pacienteId, sedeId, nombre, costo);
        return id;
    }

    protected UUID insertarPago(UUID pacienteId, String sedeId, String monto, UUID tratamientoId) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO pagos (id, sede_id, paciente_id, tratamiento_id, monto, metodo_pago) "
                + "VALUES (?, ?, ?, ?, ?::numeric, 'EFECTIVO')", id, sedeId, pacienteId, tratamientoId, monto);
        return id;
    }

    protected UUID insertarNota(UUID pacienteId, UsuarioPrueba autor) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO notas_evolucion (id, paciente_id, autor_id, autor_nombre, autor_rol, contenido, tipo_nota) "
                + "VALUES (?, ?, ?, ?, ?, 'Nota de prueba', 'TEXTO_LIBRE')", id, pacienteId, autor.id(), autor.nombre(), autor.rol());
        return id;
    }

    protected UUID insertarAntecedente(UUID pacienteId, String motivo) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO antecedentes_historial (id, paciente_id, alergias, motivo_cambio) VALUES (?, ?, 'Polen', ?)",
                id, pacienteId, motivo);
        return id;
    }

    /** Acceso a historia clinica; minutosAtras permite simular accesos antiguos (la tabla es inmutable). */
    protected UUID insertarAcceso(UUID pacienteId, UsuarioPrueba u, String sedeId, String ip, int minutosAtras) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO historia_clinica_accesos (id, paciente_id, usuario_id, usuario_nombre, usuario_email, "
                + "usuario_rol, sede_id, ip, accedido_en) VALUES (?, ?, ?, ?, ?, ?, ?, ?, now() - (? * interval '1 minute'))",
                id, pacienteId, u.id(), u.nombre(), u.email(), u.rol(), sedeId, ip, minutosAtras);
        return id;
    }

    protected UUID insertarConsentimiento(UUID pacienteId, String tipo, String firmaKey, String pdfKey, int minutosAtras) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO consentimientos (id, paciente_id, tipo, titulo, contenido_aceptado, firma_imagen_key, pdf_key, "
                + "archivo_key, nombre_firmante, documento_firmante, firmado_en) VALUES (?, ?, ?, 'Titulo SQL', 'Contenido SQL', "
                + "?, ?, ?, 'Firmante SQL', '87654321', now() - (? * interval '1 minute'))",
                id, pacienteId, tipo, firmaKey, pdfKey, pdfKey, minutosAtras);
        return id;
    }

    protected UUID insertarOdontograma(UUID pacienteId, int piezaFdi) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO odontograma_registros (id, paciente_id, pieza_fdi, estado) VALUES (?, ?, ?, 'CARIES')",
                id, pacienteId, piezaFdi);
        return id;
    }

    protected UUID insertarRadiografia(UUID pacienteId, String sedeId, String archivoUrl) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO radiografias_estudios (id, paciente_id, sede_id, tipo_estudio, archivo_url, archivo_key) "
                + "VALUES (?, ?, ?, 'PANORAMICA', ?, ?)", id, pacienteId, sedeId, archivoUrl, archivoUrl);
        return id;
    }

    /** Orden de laboratorio (FK a pacientes sin cascada) con el laboratorio y un especialista de la semilla. */
    protected UUID insertarOrdenLaboratorio(UUID pacienteId, String sedeId) {
        UUID id = UUID.randomUUID();
        UUID especialista = jdbc.queryForObject("SELECT id FROM especialistas LIMIT 1", UUID.class);
        jdbc.update("INSERT INTO ordenes_laboratorio (id, codigo, paciente_id, especialista_id, sede_id, laboratorio_id, "
                + "tipo_trabajo) VALUES (?, ?, ?, ?, ?, '50000000-0000-0000-0000-000000000001', 'CORONA')",
                id, "LAB-T-" + tag() + tag(), pacienteId, especialista, sedeId);
        return id;
    }

    /** Cuerpo valido de POST/PUT /pacientes con DNI y nombre unicos. */
    protected Map<String, Object> cuerpoPaciente(String sedeId) {
        Map<String, Object> m = new HashMap<>();
        m.put("dni", dniUnico());
        m.put("nombres", "Nombre" + tag());
        m.put("apellidos", "Apellido" + tag());
        if (sedeId != null) {
            m.put("sedeId", sedeId);
        }
        return m;
    }

    /** Filas de auditoria de aplicacion (columnas completas; ip como texto) para una accion y un registro. */
    protected List<Map<String, Object>> filasAuditoria(String accion, String registroId) {
        List<Map<String, Object>> filas = jdbc.queryForList("SELECT usuario_id, usuario_nombre, rol, sede_id, modulo, "
                + "accion, tabla, registro_id, detalle, host(ip) AS ip FROM auditoria_log WHERE accion = ? "
                + "AND registro_id = ? ORDER BY id", accion, registroId);
        List<Map<String, Object>> salida = new ArrayList<>();
        for (Map<String, Object> f : filas) {
            Map<String, Object> copia = new HashMap<>(f);
            if (copia.get("usuario_id") != null) {
                copia.put("usuario_id", copia.get("usuario_id").toString());
            }
            salida.add(copia);
        }
        return salida;
    }

    protected Map<String, Object> filaAuditoria(String accion, String registroId) {
        List<Map<String, Object>> filas = filasAuditoria(accion, registroId);
        if (filas.size() != 1) {
            throw new AssertionError("Se esperaba 1 fila de " + accion + " para " + registroId + " y hay " + filas.size());
        }
        return filas.get(0);
    }

    protected static Map<String, Object> esperadoAuditoria(Object usuarioId, String nombre, String rol, String sede,
                                                           String modulo, String accion, String tabla, String registroId,
                                                           String detalle, String ip) {
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

    // ---------------------------------------------------------------- HTTP helpers

    protected String json(Object o) throws Exception {
        return objectMapper.writeValueAsString(o);
    }

    protected JsonNode leer(MvcResult r) throws Exception {
        return objectMapper.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    /** Ejecuta la peticion con el JWT del usuario (si no es nulo), la IP indicada (si no es nula) y el cuerpo JSON. */
    protected MvcResult ejecutar(MockHttpServletRequestBuilder b, UsuarioPrueba u, String ip, Object cuerpo) throws Exception {
        if (u != null) {
            b = b.header("Authorization", u.bearer());
        }
        if (ip != null) {
            b = b.with(req -> {
                req.setRemoteAddr(ip);
                return req;
            });
        }
        if (cuerpo != null) {
            b = b.contentType(MediaType.APPLICATION_JSON)
                    .content(cuerpo instanceof String ? (String) cuerpo : json(cuerpo));
        }
        return mockMvc.perform(b).andReturn();
    }

    protected MvcResult ejecutar(MockHttpServletRequestBuilder b, UsuarioPrueba u, Object cuerpo) throws Exception {
        return ejecutar(b, u, null, cuerpo);
    }

    protected MvcResult ejecutar(MockHttpServletRequestBuilder b, UsuarioPrueba u) throws Exception {
        return ejecutar(b, u, null, null);
    }

    protected static List<String> campos(JsonNode nodo) {
        List<String> campos = new ArrayList<>();
        Iterator<String> it = nodo.fieldNames();
        it.forEachRemaining(campos::add);
        return campos;
    }

    protected static List<String> ids(JsonNode arreglo) {
        List<String> ids = new ArrayList<>();
        arreglo.forEach(n -> ids.add(n.get("id").asText()));
        return ids;
    }

    protected static JsonNode buscarPorId(JsonNode arreglo, String id) {
        for (JsonNode n : arreglo) {
            if (id.equals(n.get("id").asText())) {
                return n;
            }
        }
        return null;
    }

    /** PNG valido de 1x1 (solo importa la cabecera magica que valida el servicio). */
    protected static final String PNG_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==";

    /** Claves exactas de PacienteResponseDTO. */
    protected static final List<String> CLAVES_PACIENTE = List.of("id", "dni", "nombres", "apellidos", "nombreCompleto",
            "fechaNacimiento", "edad", "telefono", "email", "sedeId", "genero", "fuenteCaptacion",
            "referidoPorPacienteId", "referidoPorPacienteNombre", "doctorReferidor", "consentimientoPromociones",
            "consentimientoPromocionesFecha", "numeroHistoriaClinica", "totalInasistencias", "avisoInasistencias",
            "esCumpleaneroMes", "alergias", "antecedentesMedicos", "gestante", "semanasGestacion", "saldoPendiente",
            "archivado", "tieneDeuda", "ultimaCitaAtendida", "fotoActualizadaEn");
}
