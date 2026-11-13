package com.odontomax.pacientes.caracterizacion;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Flujo de «Registrar cobro» cuando el paciente no tiene un plan con saldo: la pantalla crea un plan sencillo con el precio
 * del procedimiento y cobra contra él. Con el servidor real, el primer intento debe cobrar sin error de saldo.
 */
@Testcontainers(disabledWithoutDocker = true)
class CobroPlanSimplePostgresIntegrationTest extends PacientesPostgresTestBase {

    private MvcResult crearPlan(UsuarioPrueba recepcion, String sede, UUID paciente, UUID doctor, double costo) throws Exception {
        Map<String, Object> item = Map.of("nombreProcedimiento", "Ortodoncia convencional - cuota mensual",
                "precioBase", costo, "cantidad", 1, "descuento", 0);
        Map<String, Object> cuerpo = new java.util.HashMap<>();
        cuerpo.put("pacienteId", paciente.toString());
        cuerpo.put("sedeId", sede);
        cuerpo.put("especialistaId", doctor.toString());
        cuerpo.put("nombre", "Ortodoncia convencional - cuota mensual");
        cuerpo.put("modalidadPago", "CUOTAS");
        cuerpo.put("costoTotal", costo);
        cuerpo.put("descuentoTotal", 0);
        cuerpo.put("totalSesiones", 1);
        cuerpo.put("pagadoAntesSistema", 0);
        cuerpo.put("items", List.of(item));
        return ejecutar(post("/tratamientos"), recepcion, cuerpo);
    }

    @Test
    @DisplayName("Crear el plan sencillo y cobrar su importe completo funciona al primer intento (sin 'saldo S/ 0.00')")
    void planSencilloYCobroAlPrimerIntento() throws Exception {
        String sede = crearSede();
        UsuarioPrueba admin = crearUsuario("ADMIN_SEDE", sede);
        UsuarioPrueba doctor = crearUsuario("ODONTOLOGO", sede);
        UUID paciente = insertarPaciente(sede, "Yesenis", "Yallico");
        UUID espId = jdbc.queryForObject("SELECT id FROM especialistas WHERE usuario_id = ?", UUID.class, doctor.id());

        MvcResult plan = crearPlan(admin, sede, paciente, espId, 150.0);
        assertThat(plan.getResponse().getStatus()).as(plan.getResponse().getContentAsString()).isEqualTo(201);
        JsonNode p = leer(plan);
        assertThat(p.get("costoTotal").asDouble()).isEqualTo(150.0);
        assertThat(p.get("saldoPendiente").asDouble()).isEqualTo(150.0);

        Map<String, Object> pago = new java.util.HashMap<>();
        pago.put("pacienteId", paciente.toString());
        pago.put("tratamientoId", p.get("id").asText());
        pago.put("especialistaId", espId.toString());
        pago.put("sedeId", sede);
        pago.put("monto", 150.0);
        pago.put("metodoPago", "EFECTIVO");
        pago.put("comprobanteTipo", "RECIBO");
        MvcResult cobro = ejecutar(post("/pagos"), admin, pago);

        assertThat(cobro.getResponse().getStatus()).as(cobro.getResponse().getContentAsString()).isEqualTo(201);

        // Un segundo cobro del mismo importe sobre el mismo plan sí debe rechazarse: el plan ya está pagado.
        MvcResult segundo = ejecutar(post("/pagos"), admin, pago);
        assertThat(segundo.getResponse().getStatus()).isEqualTo(409);
        assertThat(segundo.getResponse().getContentAsString()).contains("Saldo disponible: S/ 0.00");
    }

    @Test
    @DisplayName("Cobrar solo una parte (una cuota) deja saldo y no bloquea el siguiente cobro")
    void cobroParcial() throws Exception {
        String sede = crearSede();
        UsuarioPrueba admin = crearUsuario("ADMIN_SEDE", sede);
        UsuarioPrueba doctor = crearUsuario("ODONTOLOGO", sede);
        UUID paciente = insertarPaciente(sede, "Parcial", "Cuota");
        UUID espId = jdbc.queryForObject("SELECT id FROM especialistas WHERE usuario_id = ?", UUID.class, doctor.id());

        JsonNode p = leer(crearPlan(admin, sede, paciente, espId, 300.0));
        Map<String, Object> pago = new java.util.HashMap<>();
        pago.put("pacienteId", paciente.toString());
        pago.put("tratamientoId", p.get("id").asText());
        pago.put("especialistaId", espId.toString());
        pago.put("sedeId", sede);
        pago.put("monto", 100.0);
        pago.put("metodoPago", "YAPE");
        pago.put("comprobanteTipo", "RECIBO");

        assertThat(ejecutar(post("/pagos"), admin, pago).getResponse().getStatus()).isEqualTo(201);
        assertThat(ejecutar(post("/pagos"), admin, pago).getResponse().getStatus()).isEqualTo(201);

        MvcResult lista = ejecutar(get("/tratamientos/paciente/" + paciente), admin);
        assertThat(leer(lista).get(0).get("saldoPendiente").asDouble()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("Anular un cobro funciona con el cuerpo que manda la pantalla ({motivoAnulacion}) y también con el antiguo ({motivo}); devuelve el saldo y queda el egreso")
    void anularConAmbosNombresDelMotivo() throws Exception {
        String sede = crearSede();
        UsuarioPrueba admin = crearUsuario("ADMIN_SEDE", sede);
        UsuarioPrueba doctor = crearUsuario("ODONTOLOGO", sede);
        UUID paciente = insertarPaciente(sede, "Anula", "Cobro");
        UUID espId = jdbc.queryForObject("SELECT id FROM especialistas WHERE usuario_id = ?", UUID.class, doctor.id());

        JsonNode p = leer(crearPlan(admin, sede, paciente, espId, 200.0));
        for (String campo : new String[] {"motivoAnulacion", "motivo"}) {
            Map<String, Object> pago = new java.util.HashMap<>();
            pago.put("pacienteId", paciente.toString());
            pago.put("tratamientoId", p.get("id").asText());
            pago.put("especialistaId", espId.toString());
            pago.put("sedeId", sede);
            pago.put("monto", 200.0);
            pago.put("metodoPago", "YAPE");
            pago.put("comprobanteTipo", "RECIBO");
            JsonNode creado = leer(ejecutar(post("/pagos"), admin, pago));

            MvcResult anulado = ejecutar(post("/pagos/" + creado.get("id").asText() + "/anular"), admin,
                    Map.of(campo, "Método de pago equivocado (Yape, efectivo, tarjeta): era efectivo"));

            assertThat(anulado.getResponse().getStatus()).as(campo + ": " + anulado.getResponse().getContentAsString()).isEqualTo(200);
            assertThat(leer(anulado).get("anulado").asBoolean()).isTrue();
            assertThat(jdbc.queryForObject("SELECT motivo_anulacion FROM pagos WHERE id = ?::uuid", String.class, creado.get("id").asText()))
                    .contains("era efectivo");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM caja_movimientos WHERE referencia_id = ?::uuid AND tipo = 'EGRESO'",
                    Integer.class, creado.get("id").asText())).isEqualTo(1);
            assertThat(leer(ejecutar(get("/tratamientos/paciente/" + paciente), admin)).get(0).get("saldoPendiente").asDouble()).isEqualTo(200.0);
        }
    }
}
