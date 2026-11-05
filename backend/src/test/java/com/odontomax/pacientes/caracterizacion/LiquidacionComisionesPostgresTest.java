package com.odontomax.pacientes.caracterizacion;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.odontomax.radiologia.service.LiquidacionComisionesService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Pago de comisiones por lote (un solo egreso de caja, todo o nada), estado de cuenta del doctor que deriva y las
 * comisiones que no se pudieron generar al cobrar.
 */
@Testcontainers(disabledWithoutDocker = true)
class LiquidacionComisionesPostgresTest extends PacientesPostgresTestBase {

    private static final String SEDE_VILLAMAX = "sede-villamax-chilca";

    @Autowired
    private LiquidacionComisionesService liquidacionService;

    private String crearDerivador(UsuarioPrueba admin) throws Exception {
        MvcResult r = ejecutar(post("/radiologia/derivadores"), admin,
                Map.of("nombre", "Dr. Derivador " + tag(), "clinicaOrigen", "Clínica Externa"));
        assertThat(r.getResponse().getStatus()).isEqualTo(201);
        return leer(r).get("id").asText();
    }

    private String derivar(UsuarioPrueba admin, String derivadorId, String paciente) throws Exception {
        MvcResult r = ejecutar(post("/radiologia/comisiones").header("X-Negocio", "VILLAMAX"), admin,
                Map.of("doctorDerivadorId", derivadorId, "pacienteNombre", paciente, "estudioId", "vmx-panoramica", "sedeId", SEDE_VILLAMAX));
        assertThat(r.getResponse().getStatus()).isEqualTo(201);
        return leer(r).get("id").asText();
    }

    private MvcResult lote(UsuarioPrueba admin, List<String> ids, String metodo) throws Exception {
        return ejecutar(post("/radiologia/comisiones/liquidar-lote").header("X-Negocio", "VILLAMAX"), admin,
                Map.of("comisionIds", ids, "metodoPago", metodo, "codigoOperacion", "OP-LOTE"));
    }

    private BigDecimal sumaDeMontos(List<String> ids) {
        return ids.stream().map(id -> jdbc.queryForObject("SELECT monto_comision FROM comisiones_radiologicas WHERE id = ?",
                BigDecimal.class, UUID.fromString(id))).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    @DisplayName("Varias comisiones del mismo doctor se pagan juntas con un solo egreso de caja por el total")
    void liquidarEnLote() throws Exception {
        UsuarioPrueba admin = adminGeneral();
        String doctor = crearDerivador(admin);
        List<String> ids = List.of(derivar(admin, doctor, "Luciana Meza"), derivar(admin, doctor, "Renzo Huaylinos"), derivar(admin, doctor, "Mateo Ramos"));

        MvcResult r = lote(admin, ids, "PLIN");

        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        String loteId = leer(r).get("loteId").asText();
        assertThat(leer(r).get("cantidad").asInt()).isEqualTo(3);
        assertThat(contar("SELECT count(*) FROM comisiones_radiologicas WHERE lote_liquidacion_id = ? AND estado = 'LIQUIDADO'", UUID.fromString(loteId))).isEqualTo(3);
        List<Map<String, Object>> egresos = jdbc.queryForList(
                "SELECT tipo, metodo_pago, monto FROM caja_movimientos WHERE referencia_tipo = 'COMISION_RADIOLOGICA' AND referencia_id = ?", UUID.fromString(loteId));
        assertThat(egresos).hasSize(1);
        assertThat(egresos.get(0).get("tipo")).isEqualTo("EGRESO");
        assertThat(egresos.get(0).get("metodo_pago")).isEqualTo("PLIN");
        assertThat((BigDecimal) egresos.get(0).get("monto")).isEqualByComparingTo(sumaDeMontos(ids));
    }

    @Test
    @DisplayName("Todo o nada: con un doctor distinto no se liquida ninguna ni sale dinero de la caja")
    void loteConDoctoresDistintos() throws Exception {
        UsuarioPrueba admin = adminGeneral();
        String a = derivar(admin, crearDerivador(admin), "Luciana Meza");
        String b = derivar(admin, crearDerivador(admin), "Renzo Huaylinos");
        int egresosAntes = contar("SELECT count(*) FROM caja_movimientos WHERE referencia_tipo = 'COMISION_RADIOLOGICA'");

        assertThat(lote(admin, List.of(a, b), "EFECTIVO").getResponse().getStatus()).isEqualTo(400);

        assertThat(contar("SELECT count(*) FROM comisiones_radiologicas WHERE id IN (?, ?) AND estado = 'PENDIENTE'", UUID.fromString(a), UUID.fromString(b))).isEqualTo(2);
        assertThat(contar("SELECT count(*) FROM caja_movimientos WHERE referencia_tipo = 'COMISION_RADIOLOGICA'")).isEqualTo(egresosAntes);
    }

    @Test
    @DisplayName("Una comisión ya liquidada no se puede incluir en otro lote")
    void loteConComisionYaLiquidada() throws Exception {
        UsuarioPrueba admin = adminGeneral();
        String doctor = crearDerivador(admin);
        String uno = derivar(admin, doctor, "Luciana Meza");
        String dos = derivar(admin, doctor, "Renzo Huaylinos");
        assertThat(lote(admin, List.of(uno), "YAPE").getResponse().getStatus()).isEqualTo(200);

        assertThat(lote(admin, List.of(uno, dos), "YAPE").getResponse().getStatus()).isEqualTo(400);

        assertThat(jdbc.queryForObject("SELECT estado FROM comisiones_radiologicas WHERE id = ?", String.class, UUID.fromString(dos))).isEqualTo("PENDIENTE");
    }

    @Test
    @DisplayName("El estado de cuenta suma lo generado, lo pagado y lo pendiente del doctor en el periodo")
    void estadoDeCuenta() throws Exception {
        UsuarioPrueba admin = adminGeneral();
        String doctor = crearDerivador(admin);
        String pagada = derivar(admin, doctor, "Luciana Meza");
        String pendiente = derivar(admin, doctor, "Renzo Huaylinos");
        assertThat(lote(admin, List.of(pagada), "YAPE").getResponse().getStatus()).isEqualTo(200);

        MvcResult r = ejecutar(get("/radiologia/comisiones/estado-cuenta").header("X-Negocio", "VILLAMAX")
                .param("doctorId", doctor).param("desde", LocalDate.now().minusDays(1).toString()).param("hasta", LocalDate.now().plusDays(1).toString()), admin);

        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        JsonNode cuenta = leer(r);
        assertThat(cuenta.get("filas")).hasSize(2);
        assertThat(cuenta.get("totalGenerado").decimalValue()).isEqualByComparingTo(sumaDeMontos(List.of(pagada, pendiente)));
        assertThat(cuenta.get("totalPagado").decimalValue()).isEqualByComparingTo(sumaDeMontos(List.of(pagada)));
        assertThat(cuenta.get("totalPendiente").decimalValue()).isEqualByComparingTo(sumaDeMontos(List.of(pendiente)));
    }

    @Test
    @DisplayName("Una comisión que falló al generarse queda a la vista, cuenta los reintentos y desaparece al resolverse")
    void comisionesFallidas() throws Exception {
        UsuarioPrueba admin = adminGeneral();
        UUID paciente = insertarPaciente(SEDE_VILLAMAX, "Luciana", "Meza " + tag());
        UUID cita = insertarCita(paciente, SEDE_VILLAMAX, "ATENDIDA", LocalDate.now());

        liquidacionService.registrarFalloAlGenerar(cita, SEDE_VILLAMAX, "Luciana Meza", "Radiografía panorámica", "El doctor derivador está inactivo");
        liquidacionService.registrarFalloAlGenerar(cita, SEDE_VILLAMAX, "Luciana Meza", "Radiografía panorámica", "El doctor derivador está inactivo");

        JsonNode fallidas = leer(ejecutar(get("/radiologia/comisiones/fallidas").header("X-Negocio", "VILLAMAX"), admin));
        JsonNode fila = null;
        for (JsonNode f : fallidas) {
            if (cita.toString().equals(f.get("citaId").asText())) fila = f;
        }
        assertThat(fila).isNotNull();
        assertThat(fila.get("intentos").asInt()).isEqualTo(2);
        assertThat(fila.get("motivo").asText()).contains("inactivo");

        liquidacionService.resolverFallo(cita);

        JsonNode despues = leer(ejecutar(get("/radiologia/comisiones/fallidas").header("X-Negocio", "VILLAMAX"), admin));
        for (JsonNode f : despues) {
            assertThat(f.get("citaId").asText()).isNotEqualTo(cita.toString());
        }
    }
}
