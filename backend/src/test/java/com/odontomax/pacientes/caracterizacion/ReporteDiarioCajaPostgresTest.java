package com.odontomax.pacientes.caracterizacion;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Reporte diario de caja: ingresos por método, egresos por categoría y la diferencia de cada arqueo del día.
 */
@Testcontainers(disabledWithoutDocker = true)
class ReporteDiarioCajaPostgresTest extends PacientesPostgresTestBase {

    private UUID sesionCerrada(String sede, UsuarioPrueba cajero, String esperado, String contado, String esperadoYape, String contadoYape, String obs) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO caja_sesiones (id, sede_id, abierta_por, monto_apertura, cerrada_por, cerrada_en, monto_esperado, monto_contado, "
                        + "monto_esperado_yape, monto_contado_yape, monto_esperado_plin, monto_contado_plin, observaciones) "
                        + "VALUES (?, ?, ?, 0, ?, now(), ?::numeric, ?::numeric, ?::numeric, ?::numeric, 0, 0, ?)",
                id, sede, cajero.id(), cajero.id(), esperado, contado, esperadoYape, contadoYape, obs);
        return id;
    }

    private void egreso(UUID sesion, UsuarioPrueba cajero, String monto, String metodo, String tipoReferencia) {
        jdbc.update("INSERT INTO caja_movimientos (sesion_id, tipo, concepto, monto, metodo_pago, referencia_tipo, registrado_por) "
                + "VALUES (?, 'EGRESO', 'Egreso de prueba', ?::numeric, ?, ?, ?)", sesion, monto, metodo, tipoReferencia, cajero.id());
    }

    @Test
    @DisplayName("El reporte suma ingresos por método, agrupa los egresos por categoría y muestra la diferencia del arqueo")
    void reporteDelDia() throws Exception {
        String sede = crearSede();
        UsuarioPrueba recepcion = crearUsuario("RECEPCION", sede);
        UUID paciente = insertarPaciente(sede, "Luciana", "Meza " + tag());
        insertarPago(paciente, sede, "200.00", null);
        jdbc.update("INSERT INTO pagos (sede_id, paciente_id, monto, metodo_pago) VALUES (?, ?, 90.00, 'YAPE')", sede, paciente);
        UUID sesion = sesionCerrada(sede, recepcion, "170.00", "165.00", "10.00", "10.00", "Faltaron S/ 5 de cambio");
        egreso(sesion, recepcion, "30.00", "EFECTIVO", "GASTO");
        egreso(sesion, recepcion, "80.00", "YAPE", "PAGO_LABORATORIO");
        egreso(sesion, recepcion, "15.00", "PLIN", "COMISION_RADIOLOGICA");

        MvcResult r = ejecutar(get("/caja/reporte-diario").param("sedeId", sede)
                .param("fecha", LocalDate.now(ZoneId.of("America/Lima")).toString()), recepcion);

        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        JsonNode reporte = leer(r);
        assertThat(reporte.get("ingresos").get("efectivo").decimalValue()).isEqualByComparingTo("200.00");
        assertThat(reporte.get("ingresos").get("yape").decimalValue()).isEqualByComparingTo("90.00");
        assertThat(reporte.get("ingresos").get("total").decimalValue()).isEqualByComparingTo("290.00");
        assertThat(reporte.get("egresos").get("total").decimalValue()).isEqualByComparingTo("125.00");
        assertThat(reporte.get("balance").decimalValue()).isEqualByComparingTo("165.00");

        JsonNode categorias = reporte.get("egresosPorCategoria");
        assertThat(categorias).hasSize(3);
        assertThat(categorias.get(0).get("categoria").asText()).isEqualTo("Laboratorio");
        assertThat(categorias.get(0).get("monto").decimalValue()).isEqualByComparingTo("80.00");

        JsonNode cierre = reporte.get("cierres").get(0);
        assertThat(cierre.get("efectivo").get("diferencia").decimalValue()).isEqualByComparingTo(new BigDecimal("-5.00"));
        assertThat(cierre.get("yape").get("diferencia").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(cierre.get("observaciones").asText()).contains("cambio");
    }

    @Test
    @DisplayName("Un día sin movimientos da un reporte en cero, sin cierres")
    void diaSinMovimientos() throws Exception {
        String sede = crearSede();
        UsuarioPrueba recepcion = crearUsuario("RECEPCION", sede);

        JsonNode reporte = leer(ejecutar(get("/caja/reporte-diario").param("sedeId", sede).param("fecha", "2020-01-15"), recepcion));

        assertThat(reporte.get("balance").decimalValue()).isEqualByComparingTo("0");
        assertThat(reporte.get("egresosPorCategoria")).isEmpty();
        assertThat(reporte.get("cierres")).isEmpty();
    }

    @Test
    @DisplayName("Quien trabaja en otra sede no puede ver el reporte; el odontólogo tampoco")
    void accesoPorSede() throws Exception {
        String sede = crearSede();
        UsuarioPrueba otraSede = crearUsuario("RECEPCION", crearSede());
        UsuarioPrueba odontologo = crearUsuario("ODONTOLOGO", sede);

        assertThat(ejecutar(get("/caja/reporte-diario").param("sedeId", sede), otraSede).getResponse().getStatus()).isEqualTo(403);
        assertThat(ejecutar(get("/caja/reporte-diario").param("sedeId", sede), odontologo).getResponse().getStatus()).isEqualTo(403);
    }
}
