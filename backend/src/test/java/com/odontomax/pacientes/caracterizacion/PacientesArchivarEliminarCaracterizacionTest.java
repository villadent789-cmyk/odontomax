package com.odontomax.pacientes.caracterizacion;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Caracterizacion de POST /pacientes/{id}/archivar y DELETE /pacientes/{id}: reglas de deuda y motivo, registros
 * protegidos, borrado en cascada controlado, rollback, borrado de archivos R2 solo tras el commit. Congela B-044
 * (consentimientos y ordenes de laboratorio rompen la eliminacion) y B-047 (archivar no deja auditoria propia).
 */
@Testcontainers(disabledWithoutDocker = true)
class PacientesArchivarEliminarCaracterizacionTest extends PacientesPostgresTestBase {

    private static final String MENSAJE_ARCHIVADO = "Paciente archivado con éxito conservando su historia clínica intacta";
    private static final String MENSAJE_ELIMINADO = "Paciente, citas e imágenes eliminados exitosamente de la base de datos Supabase y Cloudflare R2.";
    private static final String MENSAJE_500 = "Ha ocurrido un error interno en el servidor. Por favor intente más tarde.";

    private MvcResult archivar(UsuarioPrueba u, UUID id, Object cuerpo) throws Exception {
        return ejecutar(post("/pacientes/" + id + "/archivar"), u, cuerpo);
    }

    private MvcResult eliminar(UsuarioPrueba u, UUID id, String query) throws Exception {
        return ejecutar(delete("/pacientes/" + id + query), u);
    }

    private static Map<String, Object> motivo(String texto) {
        Map<String, Object> m = new HashMap<>();
        m.put("motivo", texto);
        return m;
    }

    private static String mensajeDeuda(String saldo) {
        return "El paciente mantiene una deuda pendiente de S/ " + saldo
                + ". Es obligatorio registrar un motivo detallado (ej. Mudanza, Deuda incobrable).";
    }

    // ------------------------------------------------------------------ archivar

    @Test
    @DisplayName("Archivar sin deuda y sin motivo: 200 con el mensaje exacto, archivado=true y motivo_archivo NULL; el motivo se guarda SIN recortar")
    void archivarSinDeuda() throws Exception {
        String sede = crearSede();
        UsuarioPrueba admin = crearUsuario("ADMIN_SEDE", sede);
        UUID sinMotivo = insertarPaciente(sede, "Sin", "Motivo");
        UUID conMotivo = insertarPaciente(sede, "Con", "Motivo");

        MvcResult r = archivar(admin, sinMotivo, new HashMap<>());
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        JsonNode j = leer(r);
        assertThat(campos(j)).containsExactly("mensaje");
        assertThat(j.get("mensaje").asText()).isEqualTo(MENSAJE_ARCHIVADO);
        Map<String, Object> bd = pacienteBd(sinMotivo);
        assertThat(bd.get("archivado")).isEqualTo(true);
        assertThat(bd.get("motivo_archivo")).isNull();

        assertThat(archivar(admin, conMotivo, motivo("  Cambio de ciudad  ")).getResponse().getStatus()).isEqualTo(200);
        assertThat(pacienteBd(conMotivo).get("motivo_archivo")).isEqualTo("  Cambio de ciudad  ");
        assertThat(pacienteBd(conMotivo).get("archivado")).isEqualTo(true);
    }

    @Test
    @DisplayName("Archivar con deuda: sin motivo, de 4 caracteres, '  abc  ' o en blanco -> 400 con el saldo; 5 caracteres tras trim -> 200 y se guarda sin recortar")
    void archivarConDeuda() throws Exception {
        String sede = crearSede();
        UsuarioPrueba admin = adminGeneral();
        UUID id = insertarPaciente(sede, "Con", "Deuda");
        jdbc.update("UPDATE pacientes SET saldo_pendiente = 150 WHERE id = ?", id);

        Object[] invalidos = {new HashMap<String, Object>(), motivo(null), motivo("abcd"), motivo("  abc  "), motivo("     "), motivo("")};
        for (Object cuerpo : invalidos) {
            MvcResult r = archivar(admin, id, cuerpo);
            assertThat(r.getResponse().getStatus()).as(cuerpo.toString()).isEqualTo(400);
            JsonNode j = leer(r);
            assertThat(j.get("message").asText()).isEqualTo(mensajeDeuda("150.00"));
            assertThat(j.get("mensaje").asText()).isEqualTo(mensajeDeuda("150.00"));
            assertThat(j.get("error").asText()).isEqualTo("Bad Request");
            assertThat(pacienteBd(id).get("archivado")).as(cuerpo.toString()).isEqualTo(false);
        }
        MvcResult ok = archivar(admin, id, motivo("  abcde  "));
        assertThat(ok.getResponse().getStatus()).isEqualTo(200);
        Map<String, Object> bd = pacienteBd(id);
        assertThat(bd.get("archivado")).isEqualTo(true);
        assertThat(bd.get("motivo_archivo")).isEqualTo("  abcde  ");
        // la deuda no se toca
        assertThat(bd.get("saldo_pendiente").toString()).isEqualTo("150.00");
    }

    @Test
    @DisplayName("El archivado desaparece del listado (con y sin page), sigue accesible por id y se puede archivar de nuevo; sin deuda el segundo archivado BORRA el motivo anterior")
    void archivadoSaleDelListadoYSePuedeRearchivar() throws Exception {
        String sede = crearSede();
        UsuarioPrueba admin = crearUsuario("ADMIN_SEDE", sede);
        UUID id = insertarPaciente(sede, "Se", "Archiva");
        UUID otro = insertarPaciente(sede, "Se", "Queda");

        assertThat(archivar(admin, id, motivo("Primer motivo")).getResponse().getStatus()).isEqualTo(200);

        MvcResult lista = ejecutar(get("/pacientes"), admin);
        assertThat(ids(leer(lista))).contains(otro.toString()).doesNotContain(id.toString());
        MvcResult pagina = ejecutar(get("/pacientes").param("page", "0"), admin);
        assertThat(ids(leer(pagina).get("content"))).contains(otro.toString()).doesNotContain(id.toString());
        MvcResult porId = ejecutar(get("/pacientes/" + id), admin);
        assertThat(porId.getResponse().getStatus()).isEqualTo(200);
        assertThat(leer(porId).get("archivado").asBoolean()).isTrue();

        assertThat(archivar(admin, id, new HashMap<>()).getResponse().getStatus()).isEqualTo(200);
        assertThat(pacienteBd(id).get("motivo_archivo")).as("el segundo archivado sin motivo lo deja en NULL").isNull();
    }

    @Test
    @DisplayName("Archivar conserva consentimiento_datos_en y los demas campos (actualizacion parcial, a diferencia de PUT)")
    void archivarConservaConsentimientoDatos() throws Exception {
        String sede = crearSede();
        UUID id = insertarPaciente(sede, "Conserva", "Consentimiento");
        jdbc.update("UPDATE pacientes SET consentimiento_datos_en = now(), alergias = 'Polen', telefono = '911222333' WHERE id = ?", id);

        assertThat(archivar(adminGeneral(), id, new HashMap<>()).getResponse().getStatus()).isEqualTo(200);

        Map<String, Object> bd = pacienteBd(id);
        assertThat(bd.get("consentimiento_datos_en")).isNotNull();
        assertThat(bd.get("alergias")).isEqualTo("Polen");
        assertThat(bd.get("telefono")).isEqualTo("911222333");
        assertThat(bd.get("archivado")).isEqualTo(true);
    }

    @Test
    @DisplayName("Archivar: RECEPCION y ODONTOLOGO 403; ADMIN_SEDE de otra sede 403; inexistente 400 'Paciente no encontrado'; sin cuerpo 400; sin token 401")
    void archivarErrores() throws Exception {
        String sedeA = crearSede();
        String sedeB = crearSede();
        UUID id = insertarPaciente(sedeA, "No", "Archivable");

        for (String rol : List.of("RECEPCION", "ODONTOLOGO")) {
            assertThat(archivar(crearUsuario(rol, sedeA), id, new HashMap<>()).getResponse().getStatus()).as(rol).isEqualTo(403);
        }
        MvcResult ajena = archivar(crearUsuario("ADMIN_SEDE", sedeB), id, new HashMap<>());
        assertThat(ajena.getResponse().getStatus()).isEqualTo(403);
        assertThat(pacienteBd(id).get("archivado")).isEqualTo(false);

        MvcResult inexistente = archivar(adminGeneral(), UUID.randomUUID(), new HashMap<>());
        assertThat(inexistente.getResponse().getStatus()).isEqualTo(400);
        assertThat(leer(inexistente).get("message").asText()).isEqualTo("Paciente no encontrado");

        MvcResult sinCuerpo = ejecutar(post("/pacientes/" + id + "/archivar"), adminGeneral());
        assertThat(sinCuerpo.getResponse().getStatus()).isEqualTo(400);
        assertThat(leer(sinCuerpo).get("message").asText())
                .isEqualTo("El cuerpo de la solicitud no es un JSON válido o tiene un formato incorrecto.");

        assertThat(archivar(null, id, new HashMap<>()).getResponse().getStatus()).isEqualTo(401);
        assertThat(pacienteBd(id).get("archivado")).isEqualTo(false);
    }

    // ------------------------------------------------------------------ eliminar: precondiciones

    @Test
    @DisplayName("Eliminar exige confirmacion=true (400 con mensaje exacto, antes de buscar el paciente); solo ADMIN_GENERAL; sin token 401")
    void eliminarPrecondiciones() throws Exception {
        String sede = crearSede();
        UUID id = insertarPaciente(sede, "No", "Eliminable");
        UsuarioPrueba admin = adminGeneral();

        for (String query : List.of("", "?confirmacion=false", "?motivo=algo")) {
            MvcResult r = eliminar(admin, id, query);
            assertThat(r.getResponse().getStatus()).as(query).isEqualTo(400);
            assertThat(leer(r).get("message").asText()).isEqualTo("Se requiere confirmación explícita (confirmacion=true) "
                    + "para la eliminación permanente de la historia clínica y radiografías.");
        }
        assertThat(eliminar(admin, UUID.randomUUID(), "").getResponse().getStatus()).isEqualTo(400);
        MvcResult tipo = eliminar(admin, id, "?confirmacion=quizas");
        assertThat(tipo.getResponse().getStatus()).isEqualTo(400);
        assertThat(leer(tipo).get("message").asText()).isEqualTo("El parámetro 'confirmacion' tiene un valor inválido.");

        for (String rol : List.of("ADMIN_SEDE", "ODONTOLOGO", "RECEPCION")) {
            UsuarioPrueba u = crearUsuario(rol, sede);
            assertThat(eliminar(u, id, "?confirmacion=true").getResponse().getStatus()).as(rol).isEqualTo(403);
            assertThat(eliminar(u, id, "").getResponse().getStatus()).as(rol + " sin confirmacion").isEqualTo(403);
        }
        assertThat(eliminar(null, id, "?confirmacion=true").getResponse().getStatus()).isEqualTo(401);

        MvcResult inexistente = eliminar(admin, UUID.randomUUID(), "?confirmacion=true");
        assertThat(inexistente.getResponse().getStatus()).isEqualTo(400);
        assertThat(leer(inexistente).get("message").asText()).isEqualTo("Paciente no encontrado");
        assertThat(existePaciente(id)).isTrue();
    }

    // ------------------------------------------------------------------ eliminar: B-044 (resuelto)

    private static final String MENSAJE_CONSENTIMIENTOS = "El paciente tiene consentimientos firmados (registro legal inmutable) "
            + "y no puede eliminarse; archive la ficha en su lugar.";
    private static final String MENSAJE_LABORATORIO = "El paciente tiene órdenes de laboratorio registradas "
            + "y no puede eliminarse; archive la ficha en su lugar.";

    @Test
    @DisplayName("B-044: Eliminar sin historia: 200 con el cuerpo exacto; borra paciente, citas, odontograma, tratamientos y radiografias SOLO de ese paciente y borra sus archivos de R2")
    void eliminarSinHistoria() throws Exception {
        String sede = crearSede();
        UsuarioPrueba admin = adminGeneral();
        UUID p = insertarPaciente(sede, "Se", "Elimina");
        UUID q = insertarPaciente(sede, "Se", "Conserva");
        String urlP1 = "pacientes/" + p + "/radiografias/uno.png";
        String urlP2 = "pacientes/" + p + "/radiografias/dos.png";
        String urlQ = "pacientes/" + q + "/radiografias/otro.png";
        for (UUID pac : List.of(p, q)) {
            insertarCita(pac, sede, "PROGRAMADA", LocalDate.now().plusDays(3));
            insertarCita(pac, sede, "ATENDIDA", LocalDate.now().minusDays(3));
            insertarOdontograma(pac, 11);
            insertarTratamiento(pac, sede, "Tratamiento", "100.00");
        }
        insertarRadiografia(p, sede, urlP1);
        insertarRadiografia(p, sede, urlP2);
        insertarRadiografia(q, sede, urlQ);

        MvcResult r = eliminar(admin, p, "?confirmacion=true&motivo=Solicitud%20del%20paciente");

        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        JsonNode j = leer(r);
        assertThat(campos(j)).containsExactlyInAnyOrder("status", "mensaje");
        assertThat(j.get("status").asInt()).isEqualTo(200);
        assertThat(j.get("mensaje").asText()).isEqualTo(MENSAJE_ELIMINADO);
        assertThat(existePaciente(p)).isFalse();
        for (String tabla : List.of("citas", "odontograma_registros", "tratamientos", "radiografias_estudios")) {
            assertThat(contar("SELECT count(*) FROM " + tabla + " WHERE paciente_id = ?", p)).as(tabla + " de P").isZero();
        }
        assertThat(existePaciente(q)).isTrue();
        assertThat(contar("SELECT count(*) FROM citas WHERE paciente_id = ?", q)).isEqualTo(2);
        assertThat(contar("SELECT count(*) FROM odontograma_registros WHERE paciente_id = ?", q)).isEqualTo(1);
        assertThat(contar("SELECT count(*) FROM tratamientos WHERE paciente_id = ?", q)).isEqualTo(1);
        assertThat(contar("SELECT count(*) FROM radiografias_estudios WHERE paciente_id = ?", q)).isEqualTo(1);
        verify(storagePort, times(1)).deleteFile(urlP1);
        verify(storagePort, times(1)).deleteFile(urlP2);
        verify(storagePort, never()).deleteFile(urlQ);
        assertThat(contar("SELECT count(*) FROM auditoria_log WHERE accion = 'ELIMINAR_PACIENTE' AND registro_id = ?", p.toString()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("B-044: Un paciente sin ninguna fila dependiente se elimina (200); las citas no protegen (se borran) y un fallo de R2 no cambia la respuesta")
    void eliminarVacioCitasNoProtegenYFallaR2() throws Exception {
        String sede = crearSede();
        UsuarioPrueba admin = adminGeneral();
        UUID vacio = insertarPaciente(sede, "Paciente", "Vacio");
        assertThat(eliminar(admin, vacio, "?confirmacion=true").getResponse().getStatus()).isEqualTo(200);
        assertThat(existePaciente(vacio)).isFalse();

        UUID p = insertarPaciente(sede, "Falla", "R2");
        insertarCita(p, sede, "ATENDIDA", LocalDate.now().minusDays(1));
        String url = "pacientes/" + p + "/radiografias/falla.png";
        insertarRadiografia(p, sede, url);
        doThrow(new RuntimeException("R2 caido")).when(storagePort).deleteFile(anyString());

        MvcResult r = eliminar(admin, p, "?confirmacion=true");

        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        assertThat(existePaciente(p)).isFalse();
        assertThat(contar("SELECT count(*) FROM citas WHERE paciente_id = ?", p)).isZero();
        verify(storagePort, times(1)).deleteFile(url);
    }

    @Test
    @DisplayName("B-044: Una promocion de cumpleanos ya usada en una cita del paciente no impide eliminarlo (se borra con el paciente)")
    void eliminarConPromocionUsadaEnCita() throws Exception {
        String sede = crearSede();
        UUID p = insertarPaciente(sede, "Con", "Promocion");
        UUID cita = insertarCita(p, sede, "ATENDIDA", LocalDate.now().minusDays(1));
        jdbc.update("INSERT INTO promociones_paciente (paciente_id, sede_id, anio, tipo, beneficio, fecha_emision, fecha_vencimiento, usado, cita_id) "
                + "VALUES (?, ?, 2026, 'PORCENTAJE', 'Descuento', current_date, current_date + 7, true, ?)", p, sede, cita);

        assertThat(eliminar(adminGeneral(), p, "?confirmacion=true").getResponse().getStatus()).isEqualTo(200);

        assertThat(existePaciente(p)).isFalse();
        assertThat(contar("SELECT count(*) FROM promociones_paciente WHERE paciente_id = ?", p)).isZero();
    }

    // ------------------------------------------------------------------ eliminar: registros protegidos

    @Test
    @DisplayName("Eliminar con notas, antecedentes, accesos o pagos: 409 con los cuatro contadores en el mensaje, sin borrar nada ni tocar R2")
    void eliminarConRegistrosProtegidos() throws Exception {
        String sede = crearSede();
        UsuarioPrueba admin = adminGeneral();
        UsuarioPrueba autor = crearUsuario("ODONTOLOGO", sede);
        String sufijo = "). De acuerdo con la normativa legal de salud, el expediente únicamente puede ser ARCHIVADO.";
        String prefijo = "No se puede eliminar permanentemente el paciente porque cuenta con registros médicos o financieros protegidos (";

        UUID conNotas = insertarPaciente(sede, "Con", "Notas");
        insertarNota(conNotas, autor);
        UUID conAntecedentes = insertarPaciente(sede, "Con", "Antecedentes");
        insertarAntecedente(conAntecedentes, "Alta");
        UUID conAccesos = insertarPaciente(sede, "Con", "Accesos");
        insertarAcceso(conAccesos, autor, sede, "10.0.0.1", 0);
        UUID conPagos = insertarPaciente(sede, "Con", "Pagos");
        insertarPago(conPagos, sede, "50.00", null);
        UUID todo = insertarPaciente(sede, "Con", "Todo");
        insertarNota(todo, autor);
        insertarNota(todo, autor);
        insertarAntecedente(todo, "Uno");
        insertarAcceso(todo, autor, sede, "10.0.0.1", 0);
        insertarAcceso(todo, autor, sede, "10.0.0.2", 1);
        insertarAcceso(todo, autor, sede, "10.0.0.3", 2);
        insertarPago(todo, sede, "10.00", null);
        String url = "pacientes/" + todo + "/radiografias/protegida.png";
        insertarRadiografia(todo, sede, url);
        insertarCita(todo, sede, "PROGRAMADA", LocalDate.now().plusDays(2));

        Object[][] casos = {
                {conNotas, "notas: 1, antecedentes: 0, accesos: 0, pagos: 0"},
                {conAntecedentes, "notas: 0, antecedentes: 1, accesos: 0, pagos: 0"},
                {conAccesos, "notas: 0, antecedentes: 0, accesos: 1, pagos: 0"},
                {conPagos, "notas: 0, antecedentes: 0, accesos: 0, pagos: 1"},
                {todo, "notas: 2, antecedentes: 1, accesos: 3, pagos: 1"},
        };
        for (Object[] caso : casos) {
            UUID id = (UUID) caso[0];
            MvcResult r = eliminar(admin, id, "?confirmacion=true&motivo=prueba");
            assertThat(r.getResponse().getStatus()).as((String) caso[1]).isEqualTo(409);
            JsonNode j = leer(r);
            assertThat(j.get("status").asInt()).isEqualTo(409);
            assertThat(j.get("error").asText()).isEqualTo("Conflict");
            assertThat(j.get("message").asText()).isEqualTo(prefijo + caso[1] + sufijo);
            assertThat(existePaciente(id)).isTrue();
            assertThat(contar("SELECT count(*) FROM auditoria_log WHERE accion = 'ELIMINAR_PACIENTE' AND registro_id = ?",
                    id.toString())).isZero();
        }
        assertThat(contar("SELECT count(*) FROM citas WHERE paciente_id = ?", todo)).isEqualTo(1);
        assertThat(contar("SELECT count(*) FROM radiografias_estudios WHERE paciente_id = ?", todo)).isEqualTo(1);
        verify(storagePort, never()).deleteFile(anyString());
    }

    @Test
    @DisplayName("B-044: un paciente con un consentimiento firmado es registro protegido: 409 con mensaje claro, sin borrar nada, sin auditoria de exito y R2 intacto")
    void conConsentimientoDa409() throws Exception {
        String sede = crearSede();
        UUID p = insertarPaciente(sede, "Con", "Consentimiento");
        insertarCita(p, sede, "PROGRAMADA", LocalDate.now().plusDays(2));
        insertarOdontograma(p, 21);
        String url = "pacientes/" + p + "/radiografias/consent.png";
        insertarRadiografia(p, sede, url);
        insertarConsentimiento(p, "USO_IMAGEN", "pacientes/" + p + "/consentimientos/firma.png",
                "pacientes/" + p + "/consentimientos/doc.pdf", 5);

        MvcResult r = eliminar(adminGeneral(), p, "?confirmacion=true&motivo=prueba");

        assertThat(r.getResponse().getStatus()).isEqualTo(409);
        JsonNode j = leer(r);
        assertThat(j.get("status").asInt()).isEqualTo(409);
        assertThat(j.get("error").asText()).isEqualTo("Conflict");
        assertThat(j.get("message").asText()).isEqualTo(MENSAJE_CONSENTIMIENTOS);
        assertThat(existePaciente(p)).isTrue();
        assertThat(contar("SELECT count(*) FROM citas WHERE paciente_id = ?", p)).isEqualTo(1);
        assertThat(contar("SELECT count(*) FROM odontograma_registros WHERE paciente_id = ?", p)).isEqualTo(1);
        assertThat(contar("SELECT count(*) FROM radiografias_estudios WHERE paciente_id = ?", p)).isEqualTo(1);
        assertThat(contar("SELECT count(*) FROM consentimientos WHERE paciente_id = ?", p)).isEqualTo(1);
        assertThat(contar("SELECT count(*) FROM auditoria_log WHERE accion = 'ELIMINAR_PACIENTE' AND registro_id = ?", p.toString()))
                .isZero();
        verify(storagePort, never()).deleteFile(anyString());
    }

    @Test
    @DisplayName("B-044: lo mismo con un consentimiento registrado por la API real (POST /consentimientos)")
    void conConsentimientoRealDa409() throws Exception {
        String sede = crearSede();
        UUID p = insertarPaciente(sede, "Consentimiento", "Real");
        Map<String, Object> c = new HashMap<>();
        c.put("pacienteId", p.toString());
        c.put("tipo", "USO_IMAGEN");
        c.put("contenidoAceptado", "Acepto");
        c.put("firmaBase64", PNG_BASE64);
        c.put("nombreFirmante", "Firmante");
        c.put("documentoFirmante", "11112222");
        assertThat(ejecutar(post("/consentimientos"), crearUsuario("RECEPCION", sede), c).getResponse().getStatus()).isEqualTo(201);

        MvcResult r = eliminar(adminGeneral(), p, "?confirmacion=true");

        assertThat(r.getResponse().getStatus()).isEqualTo(409);
        assertThat(leer(r).get("message").asText()).isEqualTo(MENSAJE_CONSENTIMIENTOS);
        assertThat(existePaciente(p)).isTrue();
        assertThat(contar("SELECT count(*) FROM consentimientos WHERE paciente_id = ?", p)).isEqualTo(1);
    }

    @Test
    @DisplayName("B-044: un paciente con una orden de laboratorio es registro protegido: 409 con mensaje claro, todo intacto y R2 sin tocar")
    void conOrdenDeLaboratorioDa409() throws Exception {
        String sede = crearSede();
        UUID p = insertarPaciente(sede, "Con", "Laboratorio");
        insertarCita(p, sede, "PROGRAMADA", LocalDate.now().plusDays(2));
        insertarTratamiento(p, sede, "Corona", "300.00");
        String url = "pacientes/" + p + "/radiografias/lab.png";
        insertarRadiografia(p, sede, url);
        insertarOrdenLaboratorio(p, sede);

        MvcResult r = eliminar(adminGeneral(), p, "?confirmacion=true&motivo=prueba");

        assertThat(r.getResponse().getStatus()).isEqualTo(409);
        JsonNode j = leer(r);
        assertThat(j.get("error").asText()).isEqualTo("Conflict");
        assertThat(j.get("message").asText()).isEqualTo(MENSAJE_LABORATORIO);
        assertThat(contar("SELECT count(*) FROM ordenes_laboratorio WHERE paciente_id = ?", p)).isEqualTo(1);
        assertThat(existePaciente(p)).isTrue();
        assertThat(contar("SELECT count(*) FROM citas WHERE paciente_id = ?", p)).isEqualTo(1);
        assertThat(contar("SELECT count(*) FROM tratamientos WHERE paciente_id = ?", p)).isEqualTo(1);
        assertThat(contar("SELECT count(*) FROM radiografias_estudios WHERE paciente_id = ?", p)).isEqualTo(1);
        assertThat(contar("SELECT count(*) FROM auditoria_log WHERE accion = 'ELIMINAR_PACIENTE' AND registro_id = ?", p.toString()))
                .isZero();
        verify(storagePort, never()).deleteFile(anyString());
    }
}
