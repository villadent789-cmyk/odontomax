package com.odontomax.pacientes.caracterizacion;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Verificación de integración con Postgres (Testcontainers):
 * El odontólogo sigue pudiendo cobrar en efectivo para sus pacientes con cita,
 * la sesión de caja registra el movimiento y la recepcionista lo ve,
 * el odontólogo recibe 403 al intentar consultar /caja/{id},
 * y el cobro en efectivo sin caja abierta abre sesión automática sin error.
 */
@Testcontainers(disabledWithoutDocker = true)
class DoctorCobroCajaPostgresIntegrationTest extends PacientesPostgresTestBase {

    @Test
    @DisplayName("Flujo completo: recepcionista abre caja, odontólogo cobra en efectivo (atribuido a su especialista), recepcionista ve el movimiento, odontólogo recibe 403 en GET /caja/{id}, y cobro sin caja abierta abre sesión automática")
    void doctorCobroEnEfectivoIntegracionConCaja() throws Exception {
        String sede = crearSede();

        // Usuarios de la sede
        UsuarioPrueba recepcion = crearUsuario("RECEPCION", sede);
        UsuarioPrueba odontologo = crearUsuario("ODONTOLOGO", sede);
        UUID espId = jdbc.queryForObject("SELECT id FROM especialistas WHERE usuario_id = ?", UUID.class, odontologo.id());

        // Paciente con cita asignada al odontólogo
        UUID paciente = insertarPaciente(sede, "Carlos", "PacienteCobro");
        vincularCita(paciente, odontologo);
        UUID tratamiento = insertarPlan(paciente, sede, espId, "Consulta con cita", "300.00");

        // =========================================================================
        // (a) Una recepcionista abre la caja de la sede
        // =========================================================================
        Map<String, Object> reqApertura = Map.of(
                "sedeId", sede,
                "montoApertura", new BigDecimal("100.00"),
                "observaciones", "Apertura turno mañana recepción"
        );
        MvcResult rApertura = ejecutar(post("/caja/abrir"), recepcion, reqApertura);
        assertThat(rApertura.getResponse().getStatus()).isEqualTo(201);
        JsonNode sesionJson = leer(rApertura);
        String sesionId = sesionJson.get("id").asText();
        assertThat(sesionJson.get("estado").asText()).isEqualTo("ABIERTA");
        assertThat(sesionJson.get("montoApertura").asDouble()).isZero();

        // =========================================================================
        // (b) Un odontólogo registra POST /pagos en EFECTIVO para un paciente suyo (con cita)
        // =========================================================================
        Map<String, Object> reqPago = Map.of(
                "pacienteId", paciente.toString(),
                "tratamientoId", tratamiento.toString(),
                "sedeId", sede,
                "monto", new BigDecimal("150.00"),
                "metodoPago", "EFECTIVO",
                "comprobanteTipo", "RECIBO",
                "cajaSesionId", sesionId
        );
        MvcResult rPago = ejecutar(post("/pagos"), odontologo, reqPago);
        assertThat(rPago.getResponse().getStatus()).isEqualTo(201);
        JsonNode pagoJson = leer(rPago);
        String pagoId = pagoJson.get("id").asText();

        // =========================================================================
        // (c) El pago queda atribuido a su especialista y el movimiento aparece en la sesión de caja de la sede
        // =========================================================================
        assertThat(pagoJson.get("especialistaId").asText()).isEqualTo(espId.toString());
        assertThat(pagoJson.get("monto").asDouble()).isEqualTo(150.00);

        Integer movCount = jdbc.queryForObject(
                "SELECT count(*) FROM caja_movimientos WHERE sesion_id = ? AND referencia_id = ?",
                Integer.class, UUID.fromString(sesionId), UUID.fromString(pagoId));
        assertThat(movCount).isEqualTo(1);

        // =========================================================================
        // (d) La recepcionista consulta la caja y ve ese movimiento
        // =========================================================================
        MvcResult rCajaRec = ejecutar(get("/caja/" + sesionId), recepcion);
        assertThat(rCajaRec.getResponse().getStatus()).isEqualTo(200);
        JsonNode cajaRecJson = leer(rCajaRec);
        assertThat(cajaRecJson.get("id").asText()).isEqualTo(sesionId);
        assertThat(cajaRecJson.get("totalIngresosEfectivo").asDouble()).isEqualTo(150.00);

        JsonNode movsRec = cajaRecJson.get("movimientos");
        assertThat(movsRec.size()).isEqualTo(1);

        boolean encontradoEnCaja = false;
        for (JsonNode m : movsRec) {
            if (m.has("referenciaId") && !m.get("referenciaId").isNull() && pagoId.equals(m.get("referenciaId").asText())) {
                encontradoEnCaja = true;
                assertThat(m.get("monto").asDouble()).isEqualTo(150.00);
                assertThat(m.get("tipo").asText()).isEqualTo("INGRESO");
            }
        }
        assertThat(encontradoEnCaja).isTrue();

        // =========================================================================
        // (e) El odontólogo recibe 403 al consultar GET /caja/{id}
        // =========================================================================
        MvcResult rCajaDoc = ejecutar(get("/caja/" + sesionId), odontologo);
        assertThat(rCajaDoc.getResponse().getStatus()).isEqualTo(403);

        // =========================================================================
        // (f) Si no hay caja abierta y el odontólogo cobra en EFECTIVO, el servicio abre la sesión automática sin error
        // =========================================================================
        String sedeSinCaja = crearSede();
        UsuarioPrueba odontologoSinCaja = crearUsuario("ODONTOLOGO", sedeSinCaja);
        UUID espIdSinCaja = jdbc.queryForObject("SELECT id FROM especialistas WHERE usuario_id = ?", UUID.class, odontologoSinCaja.id());
        UUID pacienteSinCaja = insertarPaciente(sedeSinCaja, "Maria", "PacienteSinCaja");
        vincularCita(pacienteSinCaja, odontologoSinCaja);
        UUID tratamientoSinCaja = insertarPlan(pacienteSinCaja, sedeSinCaja, espIdSinCaja, "Consulta con cita", "160.00");

        // Verificar que no hay sesiones de caja en sedeSinCaja
        Integer cajasAntes = jdbc.queryForObject(
                "SELECT count(*) FROM caja_sesiones WHERE sede_id = ? AND cerrada_en IS NULL",
                Integer.class, sedeSinCaja);
        assertThat(cajasAntes).isZero();

        // El odontólogo registra pago en EFECTIVO sin proveer cajaSesionId
        Map<String, Object> reqPagoAuto = Map.of(
                "pacienteId", pacienteSinCaja.toString(),
                "tratamientoId", tratamientoSinCaja.toString(),
                "sedeId", sedeSinCaja,
                "monto", new BigDecimal("80.00"),
                "metodoPago", "EFECTIVO",
                "comprobanteTipo", "RECIBO"
        );
        MvcResult rPagoAuto = ejecutar(post("/pagos"), odontologoSinCaja, reqPagoAuto);
        assertThat(rPagoAuto.getResponse().getStatus()).isEqualTo(201);
        JsonNode pagoAutoJson = leer(rPagoAuto);
        assertThat(pagoAutoJson.get("especialistaId").asText()).isEqualTo(espIdSinCaja.toString());
        assertThat(pagoAutoJson.get("monto").asDouble()).isEqualTo(80.00);

        // Se abrió la sesión automática en sedeSinCaja
        Integer cajasDespues = jdbc.queryForObject(
                "SELECT count(*) FROM caja_sesiones WHERE sede_id = ? AND cerrada_en IS NULL",
                Integer.class, sedeSinCaja);
        assertThat(cajasDespues).isEqualTo(1);
    }

    private UUID insertarPlan(UUID pacienteId, String sedeId, UUID especialistaId, String nombre, String costo) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO tratamientos (id, paciente_id, sede_id, especialista_id, nombre, costo_total, total_sesiones) "
                        + "VALUES (?, ?, ?, ?, ?, ?::numeric, 1)",
                id, pacienteId, sedeId, especialistaId, nombre, costo);
        return id;
    }
}
