package com.odontomax.pacientes.caracterizacion;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Caracterizacion de /consentimientos: plantillas, registro (firma PNG y PDF a R2 simulado), consentimiento de
 * tratamiento de datos, validaciones exactas de la firma y listado con URLs firmadas. El almacenamiento es un spy
 * sobre el adaptador real (sin credenciales devuelve claves y URLs simuladas).
 */
@Testcontainers(disabledWithoutDocker = true)
class ConsentimientosCaracterizacionTest extends PacientesPostgresTestBase {

    private static final String URL_BASE = "https://storage.local.odontomax.test/";
    private static final String MENSAJE_500 = "Ha ocurrido un error interno en el servidor. Por favor intente más tarde.";
    private static final List<String> CLAVES_CONSENTIMIENTO = List.of("id", "pacienteId", "tratamientoId", "tipo", "plantillaId",
            "titulo", "contenidoAceptado", "firmaUrl", "pdfUrl", "nombreFirmante", "documentoFirmante", "parentescoFirmante",
            "firmadoEn");

    private Map<String, Object> cuerpoConsentimiento(UUID pacienteId, String tipo, String firma) {
        Map<String, Object> m = new HashMap<>();
        m.put("pacienteId", pacienteId == null ? null : pacienteId.toString());
        m.put("tipo", tipo);
        m.put("contenidoAceptado", "Contenido aceptado por el paciente");
        m.put("firmaBase64", firma);
        m.put("nombreFirmante", "Maria Lopez");
        m.put("documentoFirmante", "12345678");
        return m;
    }

    private MvcResult registrar(UsuarioPrueba u, Map<String, Object> cuerpo) throws Exception {
        return ejecutar(post("/consentimientos"), u, cuerpo);
    }

    private static String pngDeTamano(int bytes) {
        byte[] datos = new byte[bytes];
        byte[] cabecera = Base64.getDecoder().decode(PNG_BASE64);
        System.arraycopy(cabecera, 0, datos, 0, 8);
        return Base64.getEncoder().encodeToString(datos);
    }

    private void verificarSinSubidas() {
        verify(storagePort, never()).uploadFile(anyString(), any(), anyLong(), anyString());
    }

    // ------------------------------------------------------------------ plantillas

    @Test
    @DisplayName("Plantillas: las 3 activas de la semilla ordenadas por titulo, con sus 7 campos; una plantilla inactiva no se lista; sin token 401")
    void plantillas() throws Exception {
        String codigoInactivo = "TEST-" + tag();
        jdbc.update("INSERT INTO plantillas_consentimiento (codigo, titulo, tipo, contenido, activo) "
                + "VALUES (?, 'AAA plantilla inactiva', 'USO_IMAGEN', 'contenido', false)", codigoInactivo);
        try {
            for (String rol : List.of("ADMIN_GENERAL", "ADMIN_SEDE", "ODONTOLOGO", "RECEPCION")) {
                UsuarioPrueba u = crearUsuario(rol, "ADMIN_GENERAL".equals(rol) ? null : crearSede());
                MvcResult r = ejecutar(get("/consentimientos/plantillas"), u);
                assertThat(r.getResponse().getStatus()).as(rol).isEqualTo(200);
                JsonNode j = leer(r);
                assertThat(j.isArray()).isTrue();
                // 3 generales + 6 específicos por tratamiento (V60): exodoncia, endodoncia, ortodoncia, blanqueamiento, prótesis y menores.
                assertThat(j).hasSize(9);
                for (JsonNode n : j) {
                    assertThat(campos(n)).containsExactlyInAnyOrder("id", "codigo", "titulo", "tipo", "contenido", "version", "activo");
                    assertThat(n.get("activo").asBoolean()).isTrue();
                    assertThat(n.get("version").asInt()).isEqualTo(1);
                }
                // Primero los informados de procedimiento (7), luego datos personales e imagen.
                java.util.List<String> informados = new java.util.ArrayList<>();
                for (int i = 0; i < 7; i++) {
                    assertThat(j.get(i).get("tipo").asText()).isEqualTo("INFORMADO_PROCEDIMIENTO");
                    informados.add(j.get(i).get("codigo").asText());
                }
                assertThat(informados).contains("CONS-PROC-V1", "CONS-QUIR-V1", "CONS-ENDO-V1", "CONS-ORTO-V1", "CONS-BLANQ-V1", "CONS-PROT-V1", "CONS-MENOR-V1");
                assertThat(j.get(7).get("codigo").asText()).isEqualTo("CONS-DATOS-V1");
                assertThat(j.get(7).get("tipo").asText()).isEqualTo("TRATAMIENTO_DATOS");
                assertThat(j.get(8).get("codigo").asText()).isEqualTo("CONS-IMAGEN-V1");
                assertThat(j.get(8).get("tipo").asText()).isEqualTo("USO_IMAGEN");
            }
            assertThat(ejecutar(get("/consentimientos/plantillas"), null).getResponse().getStatus()).isEqualTo(401);
        } finally {
            jdbc.update("DELETE FROM plantillas_consentimiento WHERE codigo = ?", codigoInactivo);
        }
    }

    // ------------------------------------------------------------------ registro

    @Test
    @DisplayName("Registro con PNG valido: 201 con las 13 claves; sube firma_{ms}.png y consentimiento_{ms}.pdf a R2 con las claves canonicas; tipo en mayusculas, firmante y documento recortados, ip y usuario")
    void registroFeliz() throws Exception {
        String sede = crearSede();
        UsuarioPrueba recepcion = crearUsuario("RECEPCION", sede);
        UUID id = insertarPaciente(sede, "Firma", "Paciente");
        UUID plantilla = jdbc.queryForObject("SELECT id FROM plantillas_consentimiento WHERE codigo = 'CONS-IMAGEN-V1'", UUID.class);
        Map<String, Object> body = cuerpoConsentimiento(id, " uso_imagen ", "data:image/png;base64," + PNG_BASE64);
        body.put("titulo", "Uso de imagenes");
        body.put("plantillaId", plantilla.toString());
        body.put("nombreFirmante", "  Maria Lopez  ");
        body.put("documentoFirmante", " 12345678 ");
        body.put("parentescoFirmante", "Madre");

        // el adaptador real consume el stream al subir: se copian los bytes en el momento de la subida
        List<byte[]> subidos = new ArrayList<>();
        doAnswer(i -> {
            subidos.add(((InputStream) i.getArgument(1)).readAllBytes());
            return i.getArgument(0);
        }).when(storagePort).uploadFile(anyString(), any(), anyLong(), anyString());

        MvcResult r = ejecutar(post("/consentimientos"), recepcion, "10.20.30.40", body);

        assertThat(r.getResponse().getStatus()).isEqualTo(201);
        JsonNode j = leer(r);
        assertThat(campos(j)).containsExactlyInAnyOrderElementsOf(CLAVES_CONSENTIMIENTO);
        assertThat(j.get("pacienteId").asText()).isEqualTo(id.toString());
        assertThat(j.get("tratamientoId").isNull()).isTrue();
        assertThat(j.get("tipo").asText()).isEqualTo("USO_IMAGEN");
        assertThat(j.get("plantillaId").asText()).isEqualTo(plantilla.toString());
        assertThat(j.get("titulo").asText()).isEqualTo("Uso de imagenes");
        assertThat(j.get("contenidoAceptado").asText()).isEqualTo("Contenido aceptado por el paciente");
        assertThat(j.get("nombreFirmante").asText()).isEqualTo("Maria Lopez");
        assertThat(j.get("documentoFirmante").asText()).isEqualTo("12345678");
        assertThat(j.get("parentescoFirmante").asText()).isEqualTo("Madre");
        assertThat(OffsetDateTime.parse(j.get("firmadoEn").asText())).isBetween(OffsetDateTime.now().minusMinutes(2),
                OffsetDateTime.now().plusMinutes(1));

        Pattern clave = Pattern.compile("pacientes/" + id + "/consentimientos/firma_(\\d+)\\.png");
        Matcher m = clave.matcher(j.get("firmaUrl").asText().replace(URL_BASE, ""));
        assertThat(m.matches()).as(j.get("firmaUrl").asText()).isTrue();
        String ms = m.group(1);
        assertThat(j.get("firmaUrl").asText()).isEqualTo(URL_BASE + "pacientes/" + id + "/consentimientos/firma_" + ms + ".png");
        assertThat(j.get("pdfUrl").asText()).isEqualTo(URL_BASE + "pacientes/" + id + "/consentimientos/consentimiento_" + ms + ".pdf");

        ArgumentCaptor<String> claves = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> largos = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<String> tipos = ArgumentCaptor.forClass(String.class);
        verify(storagePort, times(2)).uploadFile(claves.capture(), any(InputStream.class), largos.capture(), tipos.capture());
        assertThat(claves.getAllValues()).containsExactly("pacientes/" + id + "/consentimientos/firma_" + ms + ".png",
                "pacientes/" + id + "/consentimientos/consentimiento_" + ms + ".pdf");
        assertThat(tipos.getAllValues()).containsExactly("image/png", "application/pdf");
        byte[] png = Base64.getDecoder().decode(PNG_BASE64);
        assertThat(largos.getAllValues().get(0)).isEqualTo((long) png.length);
        assertThat(subidos.get(0)).isEqualTo(png);
        byte[] pdf = subidos.get(1);
        assertThat(largos.getAllValues().get(1)).isEqualTo((long) pdf.length);
        assertThat(new String(Arrays.copyOf(pdf, 5), StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

        Map<String, Object> bd = jdbc.queryForMap("SELECT * FROM consentimientos WHERE id = ?", UUID.fromString(j.get("id").asText()));
        assertThat(bd.get("paciente_id")).isEqualTo(id);
        assertThat(bd.get("tipo")).isEqualTo("USO_IMAGEN");
        assertThat(bd.get("titulo")).isEqualTo("Uso de imagenes");
        assertThat(bd.get("firma_imagen_key")).isEqualTo(claves.getAllValues().get(0));
        assertThat(bd.get("pdf_key")).isEqualTo(claves.getAllValues().get(1));
        assertThat(bd.get("archivo_key")).isEqualTo(claves.getAllValues().get(1));
        assertThat(bd.get("nombre_firmante")).isEqualTo("Maria Lopez");
        assertThat(bd.get("documento_firmante")).isEqualTo("12345678");
        assertThat(bd.get("parentesco_firmante")).isEqualTo("Madre");
        assertThat(bd.get("ip_firmante")).isEqualTo("10.20.30.40");
        assertThat(bd.get("registrado_por")).isEqualTo(recepcion.id());
        assertThat(pacienteBd(id).get("consentimiento_datos_en")).as("USO_IMAGEN no fija la fecha").isNull();
    }

    @Test
    @DisplayName("Firma sin prefijo data:, titulo omitido y todos los roles: 201 para ADMIN_GENERAL, ADMIN_SEDE, ODONTOLOGO y RECEPCION")
    void registroPorRoles() throws Exception {
        String sede = crearSede();
        UUID id = insertarPaciente(sede, "Roles", "Consentimiento");
        for (String rol : List.of("ADMIN_GENERAL", "ADMIN_SEDE", "ODONTOLOGO", "RECEPCION")) {
            UsuarioPrueba u = crearUsuario(rol, "ADMIN_GENERAL".equals(rol) ? null : sede);
            if ("ODONTOLOGO".equals(rol)) vincularCita(id, u);
            MvcResult r = registrar(u, cuerpoConsentimiento(id, "INFORMADO_PROCEDIMIENTO", PNG_BASE64));
            assertThat(r.getResponse().getStatus()).as(rol).isEqualTo(201);
            assertThat(leer(r).get("titulo").isNull()).isTrue();
            assertThat(leer(r).get("parentescoFirmante").isNull()).isTrue();
        }
        assertThat(contar("SELECT count(*) FROM consentimientos WHERE paciente_id = ?", id)).isEqualTo(4);
        assertThat(registrar(null, cuerpoConsentimiento(id, "USO_IMAGEN", PNG_BASE64)).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("TRATAMIENTO_DATOS fija consentimiento_datos_en del paciente (insensible a mayusculas); con espacios alrededor se guarda recortado y tambien la fija")
    void tratamientoDeDatosFijaLaFecha() throws Exception {
        String sede = crearSede();
        UsuarioPrueba u = crearUsuario("ODONTOLOGO", sede);

        UUID exacto = insertarPaciente(sede, "Datos", "Exacto");
        vincularCita(exacto, u);
        assertThat(registrar(u, cuerpoConsentimiento(exacto, "TRATAMIENTO_DATOS", PNG_BASE64)).getResponse().getStatus()).isEqualTo(201);
        Timestamp fecha = (Timestamp) pacienteBd(exacto).get("consentimiento_datos_en");
        assertThat(fecha).isNotNull();
        assertThat(fecha.toInstant()).isBetween(OffsetDateTime.now().minusMinutes(2).toInstant(), OffsetDateTime.now().plusMinutes(1).toInstant());

        UUID minusculas = insertarPaciente(sede, "Datos", "Minusculas");
        vincularCita(minusculas, u);
        assertThat(registrar(u, cuerpoConsentimiento(minusculas, "tratamiento_datos", PNG_BASE64)).getResponse().getStatus()).isEqualTo(201);
        assertThat(pacienteBd(minusculas).get("consentimiento_datos_en")).isNotNull();
        assertThat(jdbc.queryForObject("SELECT tipo FROM consentimientos WHERE paciente_id = ?", String.class, minusculas))
                .isEqualTo("TRATAMIENTO_DATOS");

        UUID conEspacios = insertarPaciente(sede, "Datos", "ConEspacios");
        vincularCita(conEspacios, u);
        assertThat(registrar(u, cuerpoConsentimiento(conEspacios, " TRATAMIENTO_DATOS ", PNG_BASE64)).getResponse().getStatus()).isEqualTo(201);
        assertThat(jdbc.queryForObject("SELECT tipo FROM consentimientos WHERE paciente_id = ?", String.class, conEspacios))
                .as("el tipo se guarda recortado").isEqualTo("TRATAMIENTO_DATOS");
        assertThat(pacienteBd(conEspacios).get("consentimiento_datos_en"))
                .as("el tipo recortado tambien fija la fecha").isNotNull();

        UUID otro = insertarPaciente(sede, "Datos", "Otro");
        vincularCita(otro, u);
        assertThat(registrar(u, cuerpoConsentimiento(otro, "INFORMADO_PROCEDIMIENTO", PNG_BASE64)).getResponse().getStatus()).isEqualTo(201);
        assertThat(pacienteBd(otro).get("consentimiento_datos_en")).isNull();
    }

    // ------------------------------------------------------------------ firma invalida

    @Test
    @DisplayName("Firma invalida: vacia 400 (validacion), no Base64 400, sin cabecera PNG 400, mayor de 500 KB 400 y nada se sube a R2 ni se guarda")
    void firmasInvalidas() throws Exception {
        String sede = crearSede();
        UsuarioPrueba u = crearUsuario("RECEPCION", sede);
        UUID id = insertarPaciente(sede, "Firma", "Invalida");
        Object[][] casos = {
                {"", "La firma manuscrita es obligatoria.", false},
                {"   ", "La firma manuscrita es obligatoria.", false},
                {"aGVsbG8gd29ybGQ=", "El archivo de firma debe ser una imagen PNG válida (firma mágica PNG inválida).", false},
                {"data:image/png;base64,aGVsbG8gd29ybGQ=", "El archivo de firma debe ser una imagen PNG válida (firma mágica PNG inválida).", false},
                {pngDeTamano(500 * 1024 + 1), "El archivo de la firma excede el tamaño máximo permitido de 500 KB.", false},
                {"!!!no-es-base64!!!", "Illegal base64 character", true},
        };
        for (Object[] caso : casos) {
            MvcResult r = registrar(u, cuerpoConsentimiento(id, "USO_IMAGEN", (String) caso[0]));
            assertThat(r.getResponse().getStatus()).as(String.valueOf(caso[1])).isEqualTo(400);
            String mensaje = leer(r).get("message").asText();
            if ((boolean) caso[2]) {
                assertThat(mensaje).startsWith((String) caso[1]);
            } else {
                assertThat(mensaje).isEqualTo(caso[1]);
            }
        }
        verificarSinSubidas();
        assertThat(contar("SELECT count(*) FROM consentimientos WHERE paciente_id = ?", id)).isZero();
    }

    @Test
    @DisplayName("Una firma 'data:image/png;base64,' sin datos provoca ArrayIndexOutOfBounds: 500 (no es un 400 controlado); exactamente 500 KB si se acepta")
    void firmaSinDatosDa500YLimiteExacto() throws Exception {
        String sede = crearSede();
        UsuarioPrueba u = crearUsuario("RECEPCION", sede);
        UUID id = insertarPaciente(sede, "Firma", "Limite");

        MvcResult vacia = registrar(u, cuerpoConsentimiento(id, "USO_IMAGEN", "data:image/png;base64,"));
        assertThat(vacia.getResponse().getStatus()).isEqualTo(500);
        assertThat(leer(vacia).get("message").asText()).isEqualTo(MENSAJE_500);
        verificarSinSubidas();

        MvcResult limite = registrar(u, cuerpoConsentimiento(id, "USO_IMAGEN", pngDeTamano(500 * 1024)));
        assertThat(limite.getResponse().getStatus()).isEqualTo(201);
        assertThat(contar("SELECT count(*) FROM consentimientos WHERE paciente_id = ?", id)).isEqualTo(1);
    }

    @Test
    @DisplayName("Validaciones del cuerpo: 400 con el mensaje exacto de cada campo y sin subidas")
    void validacionesDelCuerpo() throws Exception {
        String sede = crearSede();
        UsuarioPrueba u = crearUsuario("RECEPCION", sede);
        UUID id = insertarPaciente(sede, "Valida", "Consentimiento");
        Object[][] casos = {
                {"pacienteId", null, "El ID del paciente es obligatorio."},
                {"tipo", "  ", "El tipo de consentimiento es obligatorio (TRATAMIENTO_DATOS, INFORMADO_PROCEDIMIENTO, USO_IMAGEN)."},
                {"contenidoAceptado", "", "El contenido aceptado es obligatorio."},
                {"nombreFirmante", "", "El nombre del firmante es obligatorio."},
                {"documentoFirmante", " ", "El documento de identidad del firmante es obligatorio."},
                {"titulo", "t".repeat(201), "El título no puede superar 200 caracteres."},
                {"nombreFirmante", "n".repeat(151), "El nombre del firmante no puede superar 150 caracteres."},
                {"documentoFirmante", "9".repeat(31), "El documento no puede superar 30 caracteres."},
                {"parentescoFirmante", "p".repeat(51), "El parentesco no puede superar 50 caracteres."},
        };
        for (Object[] caso : casos) {
            Map<String, Object> body = cuerpoConsentimiento(id, "USO_IMAGEN", PNG_BASE64);
            body.put((String) caso[0], caso[1]);
            MvcResult r = registrar(u, body);
            assertThat(r.getResponse().getStatus()).as((String) caso[0]).isEqualTo(400);
            assertThat(leer(r).get("detalles").get((String) caso[0]).asText()).isEqualTo(caso[2]);
        }
        verificarSinSubidas();
        assertThat(contar("SELECT count(*) FROM consentimientos WHERE paciente_id = ?", id)).isZero();
    }

    @Test
    @DisplayName("B-052: un 'tipo' fuera de la lista (el CHECK de la BD lo rechaza) pasa la validacion, sube los dos archivos a R2 y falla con 500 sin limpiarlos (huerfanos)")
    void b052TipoInvalidoDejaArchivosHuerfanos() throws Exception {
        String sede = crearSede();
        UsuarioPrueba u = crearUsuario("RECEPCION", sede);
        UUID id = insertarPaciente(sede, "Tipo", "Invalido");

        MvcResult r = registrar(u, cuerpoConsentimiento(id, "OTRO_TIPO", PNG_BASE64));

        assertThat(r.getResponse().getStatus()).isEqualTo(500);
        assertThat(leer(r).get("message").asText()).isEqualTo(MENSAJE_500);
        verify(storagePort, times(2)).uploadFile(anyString(), any(), anyLong(), anyString());
        verify(storagePort, never()).deleteFile(anyString());
        assertThat(contar("SELECT count(*) FROM consentimientos WHERE paciente_id = ?", id)).isZero();
        assertThat(contar("SELECT count(*) FROM auditoria_log WHERE accion = 'REGISTRAR_CONSENTIMIENTO' AND detalle LIKE '%OTRO_TIPO%'")).isZero();
    }

    // ------------------------------------------------------------------ errores de acceso y de almacenamiento

    @Test
    @DisplayName("Paciente inexistente 400 con el id; sede ajena 403; en ambos casos no se sube nada")
    void pacienteInexistenteYSedeAjena() throws Exception {
        String sedeA = crearSede();
        String sedeB = crearSede();
        UUID enA = insertarPaciente(sedeA, "En", "SedeA");
        UUID inexistente = UUID.randomUUID();

        MvcResult noExiste = registrar(adminGeneral(), cuerpoConsentimiento(inexistente, "USO_IMAGEN", PNG_BASE64));
        assertThat(noExiste.getResponse().getStatus()).isEqualTo(400);
        assertThat(leer(noExiste).get("message").asText()).isEqualTo("No se encontró el paciente con ID: " + inexistente);

        for (String rol : List.of("ADMIN_SEDE", "ODONTOLOGO", "RECEPCION")) {
            MvcResult ajena = registrar(crearUsuario(rol, sedeB), cuerpoConsentimiento(enA, "USO_IMAGEN", PNG_BASE64));
            assertThat(ajena.getResponse().getStatus()).as(rol).isEqualTo(403);
        }
        verificarSinSubidas();
        assertThat(contar("SELECT count(*) FROM consentimientos WHERE paciente_id = ?", enA)).isZero();
    }

    @Test
    @DisplayName("Si el almacenamiento falla al subir, el registro responde 500 y no se guarda nada (ni consentimiento ni fecha de datos)")
    void falloDeAlmacenamientoAlSubir() throws Exception {
        String sede = crearSede();
        UsuarioPrueba u = crearUsuario("ADMIN_SEDE", sede);
        UUID id = insertarPaciente(sede, "Falla", "Almacenamiento");
        doThrow(new RuntimeException("R2 no disponible")).when(storagePort).uploadFile(anyString(), any(), anyLong(), anyString());

        MvcResult r = registrar(u, cuerpoConsentimiento(id, "TRATAMIENTO_DATOS", PNG_BASE64));

        assertThat(r.getResponse().getStatus()).isEqualTo(500);
        assertThat(contar("SELECT count(*) FROM consentimientos WHERE paciente_id = ?", id)).isZero();
        assertThat(pacienteBd(id).get("consentimiento_datos_en")).isNull();
    }

    // ------------------------------------------------------------------ listado

    @Test
    @DisplayName("Listado: orden por fecha de firma descendente, URLs firmadas de 15 min simuladas, nulas si no hay clave; los 4 roles de la sede; 403/400/401")
    void listado() throws Exception {
        String sede = crearSede();
        String ajena = crearSede();
        UUID id = insertarPaciente(sede, "Lista", "Consentimientos");
        UUID viejo = insertarConsentimiento(id, "USO_IMAGEN", "k/firma-vieja.png", "k/doc-viejo.pdf", 120);
        UUID nuevo = insertarConsentimiento(id, "TRATAMIENTO_DATOS", "k/firma-nueva.png", "k/doc-nuevo.pdf", 5);
        UUID sinClaves = insertarConsentimiento(id, "INFORMADO_PROCEDIMIENTO", null, null, 60);

        for (String rol : List.of("ADMIN_GENERAL", "ADMIN_SEDE", "ODONTOLOGO", "RECEPCION")) {
            UsuarioPrueba u = crearUsuario(rol, "ADMIN_GENERAL".equals(rol) ? null : sede);
            if ("ODONTOLOGO".equals(rol)) vincularCita(id, u);
            MvcResult r = ejecutar(get("/consentimientos/pacientes/" + id), u);
            assertThat(r.getResponse().getStatus()).as(rol).isEqualTo(200);
            JsonNode j = leer(r);
            assertThat(ids(j)).as(rol).containsExactly(nuevo.toString(), sinClaves.toString(), viejo.toString());
            for (JsonNode n : j) {
                assertThat(campos(n)).containsExactlyInAnyOrderElementsOf(CLAVES_CONSENTIMIENTO);
            }
            assertThat(j.get(0).get("firmaUrl").asText()).isEqualTo(URL_BASE + "k/firma-nueva.png");
            assertThat(j.get(0).get("pdfUrl").asText()).isEqualTo(URL_BASE + "k/doc-nuevo.pdf");
            assertThat(j.get(0).get("tipo").asText()).isEqualTo("TRATAMIENTO_DATOS");
            assertThat(j.get(0).get("titulo").asText()).isEqualTo("Titulo SQL");
            assertThat(j.get(1).get("firmaUrl").isNull()).isTrue();
            assertThat(j.get(1).get("pdfUrl").isNull()).isTrue();
        }
        verify(storagePort, atLeastOnce()).generatePresignedUrl(eq("k/firma-nueva.png"), eq(15));

        assertThat(ejecutar(get("/consentimientos/pacientes/" + id), crearUsuario("RECEPCION", ajena)).getResponse().getStatus()).isEqualTo(403);
        UUID inexistente = UUID.randomUUID();
        MvcResult noExiste = ejecutar(get("/consentimientos/pacientes/" + inexistente), adminGeneral());
        assertThat(noExiste.getResponse().getStatus()).isEqualTo(400);
        assertThat(leer(noExiste).get("message").asText()).isEqualTo("No se encontró el paciente con ID: " + inexistente);
        assertThat(ejecutar(get("/consentimientos/pacientes/" + id), null).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("Listado: si el almacenamiento no puede firmar las URLs se devuelven nulas con 200 (el error se traga)")
    void listadoConAlmacenamientoCaido() throws Exception {
        String sede = crearSede();
        UUID id = insertarPaciente(sede, "Lista", "SinR2");
        insertarConsentimiento(id, "USO_IMAGEN", "k/f.png", "k/d.pdf", 1);
        doThrow(new RuntimeException("R2 caido")).when(storagePort).generatePresignedUrl(anyString(), anyInt());

        UsuarioPrueba doc = crearUsuario("ODONTOLOGO", sede);
        vincularCita(id, doc);
        MvcResult r = ejecutar(get("/consentimientos/pacientes/" + id), doc);

        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        JsonNode j = leer(r);
        assertThat(j).hasSize(1);
        assertThat(j.get(0).get("firmaUrl").isNull()).isTrue();
        assertThat(j.get(0).get("pdfUrl").isNull()).isTrue();
        assertThat(j.get(0).get("tipo").asText()).isEqualTo("USO_IMAGEN");
    }
}
