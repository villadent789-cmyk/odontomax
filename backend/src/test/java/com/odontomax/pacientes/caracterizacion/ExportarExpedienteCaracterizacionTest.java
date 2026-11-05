package com.odontomax.pacientes.caracterizacion;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Caracterizacion de GET /pacientes/{id}/exportar (derecho ARCO): contenido agregado de los modulos, permisos,
 * 404 propio de este endpoint, auditoria EXPORTAR_ARCO y ausencia de registro de acceso a historia (B-048).
 */
@Testcontainers(disabledWithoutDocker = true)
class ExportarExpedienteCaracterizacionTest extends PacientesPostgresTestBase {

    private static final String AVISO_LEGAL = "Documento clínico y administrativo confidencial expedido en cumplimiento del "
            + "Derecho ARCO de Acceso (Ley N° 29733 de Protección de Datos Personales de la República del Perú y su "
            + "Reglamento D.S. 003-2013-JUS). Prohibida su divulgación o uso no autorizado.";

    private record Expediente(UUID paciente, UUID cita, UUID tratamiento, UUID pago, UUID nota, UUID consentimiento, UUID acceso) {
    }

    private Expediente crearExpediente(String sede, String nombres, UsuarioPrueba autor) {
        UUID p = insertarPaciente(sede, nombres, "Expediente");
        UUID cita = insertarCita(p, sede, "ATENDIDA", LocalDate.now().minusDays(2));
        UUID tratamiento = insertarTratamiento(p, sede, "Endodoncia " + nombres, "200.00");
        UUID pago = insertarPago(p, sede, "50.00", tratamiento);
        UUID nota = insertarNota(p, autor);
        insertarOdontograma(p, 16);
        UUID consentimiento = insertarConsentimiento(p, "USO_IMAGEN", "k/f-" + nombres + ".png", "k/d-" + nombres + ".pdf", 3);
        UUID acceso = insertarAcceso(p, autor, sede, "10.0.0.5", 3);
        return new Expediente(p, cita, tratamiento, pago, nota, consentimiento, acceso);
    }

    private MvcResult exportar(UsuarioPrueba u, UUID id) throws Exception {
        return ejecutar(get("/pacientes/" + id + "/exportar"), u);
    }

    @Test
    @DisplayName("Exportar: 200 con las 10 claves, el aviso legal exacto y SOLO los datos del paciente (citas, tratamientos, pagos, notas, odontograma, consentimientos y accesos)")
    void exportaElExpedienteDelPaciente() throws Exception {
        String sede = crearSede();
        UsuarioPrueba admin = crearUsuario("ADMIN_SEDE", sede);
        UsuarioPrueba odontologo = crearUsuario("ODONTOLOGO", sede);
        Expediente p = crearExpediente(sede, "Primero", odontologo);
        Expediente otro = crearExpediente(sede, "Segundo", odontologo);
        jdbc.update("UPDATE pacientes SET alergias = 'Polen', telefono = '955444333' WHERE id = ?", p.paciente());

        MvcResult r = exportar(admin, p.paciente());

        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        JsonNode j = leer(r);
        assertThat(campos(j)).containsExactlyInAnyOrder("datosPersonales", "citas", "tratamientos", "pagos", "notasEvolucion",
                "odontograma", "consentimientos", "accesosAuditoria", "generadoEn", "avisoLegal");
        assertThat(j.get("avisoLegal").asText()).isEqualTo(AVISO_LEGAL);
        assertThat(OffsetDateTime.parse(j.get("generadoEn").asText())).isBetween(OffsetDateTime.now().minusMinutes(2),
                OffsetDateTime.now().plusMinutes(1));

        JsonNode datos = j.get("datosPersonales");
        assertThat(campos(datos)).containsExactlyInAnyOrderElementsOf(CLAVES_PACIENTE);
        assertThat(datos.get("id").asText()).isEqualTo(p.paciente().toString());
        assertThat(datos.get("nombreCompleto").asText()).isEqualTo("Primero Expediente");
        assertThat(datos.get("telefono").asText()).isEqualTo("955444333");
        assertThat(datos.get("ultimaCitaAtendida").isNull()).isTrue();

        assertThat(ids(j.get("citas"))).containsExactly(p.cita().toString());
        assertThat(ids(j.get("tratamientos"))).containsExactly(p.tratamiento().toString());
        assertThat(ids(j.get("pagos"))).containsExactly(p.pago().toString());
        assertThat(ids(j.get("notasEvolucion"))).containsExactly(p.nota().toString());
        assertThat(ids(j.get("consentimientos"))).containsExactly(p.consentimiento().toString());
        assertThat(ids(j.get("accesosAuditoria"))).containsExactly(p.acceso().toString());
        assertThat(j.get("odontograma")).hasSize(1);
        assertThat(j.get("odontograma").get(0).get("pacienteId").asText()).isEqualTo(p.paciente().toString());
        assertThat(j.get("odontograma").get(0).get("piezaFdi").asInt()).isEqualTo(16);
        assertThat(j.get("citas").get(0).get("pacienteId").asText()).isEqualTo(p.paciente().toString());
        assertThat(j.get("pagos").get(0).get("pacienteDni").asText()).isEqualTo(pacienteBd(p.paciente()).get("dni"));
        assertThat(j.get("consentimientos").get(0).get("firmaUrl").asText())
                .isEqualTo("https://storage.local.odontomax.test/k/f-Primero.png");
        assertThat(j.toString()).doesNotContain(otro.paciente().toString());
    }

    @Test
    @DisplayName("Exportar un paciente sin historia: las siete colecciones vienen vacias")
    void exportaPacienteSinNada() throws Exception {
        String sede = crearSede();
        UUID id = insertarPaciente(sede, "Vacio", "Expediente");

        JsonNode j = leer(exportar(adminGeneral(), id));

        for (String clave : List.of("citas", "tratamientos", "pagos", "notasEvolucion", "odontograma", "consentimientos", "accesosAuditoria")) {
            assertThat(j.get(clave).isArray()).as(clave).isTrue();
            assertThat(j.get(clave)).as(clave).isEmpty();
        }
    }

    @Test
    @DisplayName("Exportar deja una fila EXPORTAR_ARCO por exportacion y NO registra acceso a la historia clinica (B-048)")
    void exportarAuditaPeroNoRegistraAcceso() throws Exception {
        String sede = crearSede();
        UsuarioPrueba admin = crearUsuario("ADMIN_SEDE", sede);
        UUID id = insertarPaciente(sede, "Audita", "Exporta");

        assertThat(exportar(admin, id).getResponse().getStatus()).isEqualTo(200);
        assertThat(exportar(admin, id).getResponse().getStatus()).isEqualTo(200);

        assertThat(filasAuditoria("EXPORTAR_ARCO", id.toString())).hasSize(2);
        assertThat(contar("SELECT count(*) FROM historia_clinica_accesos WHERE paciente_id = ?", id)).isZero();
        assertThat(filasAuditoria("APERTURA_HISTORIA", id.toString())).isEmpty();
        assertThat(filasAuditoria("LECTURA_EXPEDIENTE", id.toString())).isEmpty();
        // las lecturas de GET /pacientes/{id} tampoco registran acceso
        assertThat(ejecutar(get("/pacientes/" + id), admin).getResponse().getStatus()).isEqualTo(200);
        assertThat(contar("SELECT count(*) FROM historia_clinica_accesos WHERE paciente_id = ?", id)).isZero();
    }

    @Test
    @DisplayName("Permisos: ADMIN_GENERAL y ADMIN_SEDE exportan; ODONTOLOGO y RECEPCION 403 aun en su sede; ADMIN_SEDE de otra sede 403; sin token 401")
    void permisos() throws Exception {
        String sedeA = crearSede();
        String sedeB = crearSede();
        UUID id = insertarPaciente(sedeA, "Permisos", "Exportar");

        assertThat(exportar(adminGeneral(), id).getResponse().getStatus()).isEqualTo(200);
        assertThat(exportar(crearUsuario("ADMIN_SEDE", sedeA), id).getResponse().getStatus()).isEqualTo(200);
        for (String rol : List.of("ODONTOLOGO", "RECEPCION")) {
            assertThat(exportar(crearUsuario(rol, sedeA), id).getResponse().getStatus()).as(rol).isEqualTo(403);
        }
        MvcResult ajena = exportar(crearUsuario("ADMIN_SEDE", sedeB), id);
        assertThat(ajena.getResponse().getStatus()).isEqualTo(403);
        assertThat(leer(ajena).get("message").asText()).contains("no tiene permisos para acceder a la sede '" + sedeA + "'");
        assertThat(exportar(null, id).getResponse().getStatus()).isEqualTo(401);
        assertThat(filasAuditoria("EXPORTAR_ARCO", id.toString())).as("solo las dos exportaciones permitidas").hasSize(2);
    }

    @Test
    @DisplayName("Paciente inexistente: 404 'Paciente no encontrado' (unico endpoint del modulo con 404; los demas dan 400), tambien para un ADMIN_SEDE")
    void inexistenteDa404() throws Exception {
        for (UsuarioPrueba u : List.of(adminGeneral(), crearUsuario("ADMIN_SEDE", crearSede()))) {
            MvcResult r = exportar(u, UUID.randomUUID());
            assertThat(r.getResponse().getStatus()).as(u.rol()).isEqualTo(404);
            JsonNode j = leer(r);
            assertThat(j.get("status").asInt()).isEqualTo(404);
            assertThat(j.get("error").asText()).isEqualTo("Not Found");
            assertThat(j.get("message").asText()).isEqualTo("Paciente no encontrado");
            assertThat(j.get("mensaje").asText()).isEqualTo("Paciente no encontrado");
        }
        // un ODONTOLOGO no llega a buscar al paciente: 403 antes que 404
        assertThat(exportar(crearUsuario("ODONTOLOGO", crearSede()), UUID.randomUUID()).getResponse().getStatus()).isEqualTo(403);
    }
}
