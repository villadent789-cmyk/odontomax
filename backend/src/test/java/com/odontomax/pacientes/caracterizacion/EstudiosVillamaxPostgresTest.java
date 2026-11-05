package com.odontomax.pacientes.caracterizacion;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * V41: los estudios de Villamax viven en el catálogo de servicios, separados por negocio; la derivación de un doctor
 * externo calcula sola su comisión y al liquidarla sale como egreso de la caja de Villamax.
 */
@Testcontainers(disabledWithoutDocker = true)
class EstudiosVillamaxPostgresTest extends PacientesPostgresTestBase {

    private static final String SEDE_VILLAMAX = "sede-villamax-chilca";
    private static final String SEDE_CHILCA_1 = "sede-chilca-1";

    private Set<String> ids(MvcResult r) throws Exception {
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        Set<String> ids = new HashSet<>();
        leer(r).forEach(n -> ids.add(n.get("id").asText()));
        return ids;
    }

    private String crearDerivador(UsuarioPrueba admin) throws Exception {
        MvcResult r = ejecutar(post("/radiologia/derivadores"), admin,
                Map.of("nombre", "Dr. Derivador " + tag(), "clinicaOrigen", "Clínica Externa"));
        assertThat(r.getResponse().getStatus()).isEqualTo(201);
        return leer(r).get("id").asText();
    }

    private Map<String, Object> derivacion(String derivadorId, String pacienteNombre) {
        return Map.of("doctorDerivadorId", derivadorId, "pacienteNombre", pacienteNombre,
                "estudioId", "vmx-panoramica", "sedeId", SEDE_VILLAMAX);
    }

    @Test
    @DisplayName("V41: el catálogo de Villamax y el de Villadent no se mezclan, tampoco en la web pública")
    void catalogosSeparadosPorNegocio() throws Exception {
        UsuarioPrueba admin = adminGeneral();

        Set<String> villamax = ids(ejecutar(get("/servicios").header("X-Negocio", "VILLAMAX"), admin));
        assertThat(villamax).contains("vmx-panoramica", "vmx-cefalometrica", "vmx-periapical", "vmx-tomografia-3d");

        Set<String> villadent = ids(ejecutar(get("/servicios").header("X-Negocio", "VILLADENT"), admin));
        assertThat(villadent).isNotEmpty().doesNotContain("vmx-panoramica", "vmx-tomografia-3d");
        assertThat(ids(ejecutar(get("/reservas-publicas/catalogo/servicios"), null))).doesNotContain("vmx-panoramica");
        assertThat(ids(ejecutar(get("/reservas-publicas/catalogo/servicios?negocio=VILLADENT"), null))).doesNotContain("vmx-panoramica");
        assertThat(ids(ejecutar(get("/reservas-publicas/catalogo/servicios?negocio=VILLAMAX"), null))).contains("vmx-panoramica");

        Set<String> sedesVillamax = ids(ejecutar(get("/sedes?negocio=VILLAMAX"), null));
        assertThat(sedesVillamax).contains(SEDE_VILLAMAX).doesNotContain(SEDE_CHILCA_1);
        Set<String> sedesVilladent = ids(ejecutar(get("/sedes?negocio=VILLADENT"), null));
        assertThat(sedesVilladent).contains(SEDE_CHILCA_1).doesNotContain(SEDE_VILLAMAX);
    }

    @Test
    @DisplayName("V52: el tarifario del cliente queda separado por negocio y no llena la reserva web")
    void tarifarioDelClienteClasificadoPorNegocio() throws Exception {
        UsuarioPrueba admin = adminGeneral();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM servicios_web WHERE id LIKE 'tar-%' AND unidad_de_negocio = 'VILLADENT'", Integer.class)).isEqualTo(50);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM servicios_web WHERE id LIKE 'tar-%' AND unidad_de_negocio <> 'VILLADENT'", Integer.class)).isZero();

        Set<String> internoVilladent = ids(ejecutar(get("/servicios").header("X-Negocio", "VILLADENT"), admin));
        assertThat(internoVilladent).contains("tar-consulta-normal", "tar-endo-multi", "tar-carilla-zirconio", "tar-orto-pitts21-mensual");
        assertThat(internoVilladent.stream().filter(i -> i.startsWith("vmx-"))).isEmpty();

        Set<String> internoVillamax = ids(ejecutar(get("/servicios").header("X-Negocio", "VILLAMAX"), admin));
        assertThat(internoVillamax).contains("vmx-panoramica", "vmx-bite-wing", "vmx-oclusal", "vmx-carpal", "vmx-tomografia-3d");
        assertThat(internoVillamax.stream().filter(i -> i.startsWith("tar-"))).isEmpty();
        assertThat(jdbc.queryForObject("SELECT precio_desde FROM servicios_web WHERE id = 'vmx-tomografia-3d'", java.math.BigDecimal.class))
                .isEqualByComparingTo("190.00");
        assertThat(jdbc.queryForObject("SELECT precio_desde FROM servicios_web WHERE id = 'tar-ppr-porcelana'", java.math.BigDecimal.class))
                .isEqualByComparingTo("2000.00");

        // la web y la reserva muestran el tarifario del cliente (V74) y ya no los servicios genéricos de ejemplo
        Set<String> publico = ids(ejecutar(get("/reservas-publicas/catalogo/servicios?negocio=VILLADENT"), null));
        assertThat(publico).contains("tar-profilaxis", "tar-liss-simple");
        assertThat(publico.stream().filter(i -> i.startsWith("srv-"))).isEmpty();
    }

    @Test
    @DisplayName("Un servicio creado con el negocio Villamax activo queda en Villamax con su comisión")
    void servicioNuevoQuedaEnElNegocioActivo() throws Exception {
        UsuarioPrueba admin = adminGeneral();
        Map<String, Object> cuerpo = Map.of("nombre", "Estudio de prueba " + tag(), "especialidad", "Radiología",
                "duracionMin", 20, "precio", 80, "comisionPorcentaje", 12.5);

        MvcResult creado = ejecutar(post("/servicios").header("X-Negocio", "VILLAMAX"), admin, cuerpo);
        assertThat(creado.getResponse().getStatus()).isEqualTo(201);
        String id = leer(creado).get("id").asText();
        assertThat(leer(creado).get("comisionPorcentaje").decimalValue()).isEqualByComparingTo("12.5");

        assertThat(ids(ejecutar(get("/servicios").header("X-Negocio", "VILLAMAX"), admin))).contains(id);
        assertThat(ids(ejecutar(get("/servicios").header("X-Negocio", "VILLADENT"), admin))).doesNotContain(id);
    }

    @Test
    @DisplayName("Derivación con estudio del catálogo: el doctor de Villamax la registra, la comisión sale sola y se liquida como egreso de caja")
    void derivacionYLiquidacion() throws Exception {
        UsuarioPrueba admin = adminGeneral();
        UsuarioPrueba doctorVillamax = crearUsuario("ODONTOLOGO", SEDE_VILLAMAX);
        String derivadorId = crearDerivador(admin);

        MvcResult registrada = ejecutar(post("/radiologia/comisiones"), doctorVillamax, derivacion(derivadorId, "Luciana Meza"));
        assertThat(registrada.getResponse().getStatus()).isEqualTo(201);
        JsonNode comision = leer(registrada);
        assertThat(comision.get("tipoEstudio").asText()).isEqualTo("Radiografía panorámica");
        assertThat(comision.get("precioCobrado").decimalValue()).isEqualByComparingTo("50.00");
        assertThat(comision.get("estado").asText()).isEqualTo("PENDIENTE");
        String id = comision.get("id").asText();
        // el doctor no ve el monto de la comisión; el administrador general sí
        assertThat(comision.get("montoComision").isNull()).isTrue();
        assertThat(leer(ejecutar(get("/radiologia/comisiones/" + id), admin)).get("montoComision").decimalValue())
                .isEqualByComparingTo("10.00");

        // el doctor registra pero no liquida
        assertThat(ejecutar(patch("/radiologia/comisiones/" + id + "/liquidar"), doctorVillamax, Map.of("metodoPago", "PLIN"))
                .getResponse().getStatus()).isEqualTo(403);

        MvcResult liquidada = ejecutar(patch("/radiologia/comisiones/" + id + "/liquidar"), admin,
                Map.of("metodoPago", "PLIN", "codigoOperacion", "OP-1"));
        assertThat(liquidada.getResponse().getStatus()).isEqualTo(200);
        assertThat(leer(liquidada).get("estado").asText()).isEqualTo("LIQUIDADO");

        List<Map<String, Object>> egresos = jdbc.queryForList(
                "SELECT m.tipo, m.metodo_pago, m.monto, s.sede_id FROM caja_movimientos m JOIN caja_sesiones s ON s.id = m.sesion_id "
                        + "WHERE m.referencia_tipo = 'COMISION_RADIOLOGICA' AND m.referencia_id = ?", UUID.fromString(id));
        assertThat(egresos).hasSize(1);
        assertThat(egresos.get(0).get("tipo")).isEqualTo("EGRESO");
        assertThat(egresos.get(0).get("metodo_pago")).isEqualTo("PLIN");
        assertThat((BigDecimal) egresos.get(0).get("monto")).isEqualByComparingTo("10.00");
        assertThat(egresos.get(0).get("sede_id")).isEqualTo(SEDE_VILLAMAX);

        // no se liquida dos veces
        assertThat(ejecutar(patch("/radiologia/comisiones/" + id + "/liquidar"), admin, Map.of("metodoPago", "PLIN"))
                .getResponse().getStatus()).isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM caja_movimientos WHERE referencia_id = ?", Integer.class,
                UUID.fromString(id))).isEqualTo(1);
    }

    @Test
    @DisplayName("Un doctor de Villadent no registra derivaciones de Villamax")
    void doctorDeVilladentNoRegistra() throws Exception {
        UsuarioPrueba admin = adminGeneral();
        UsuarioPrueba doctorClinica = crearUsuario("ODONTOLOGO", crearSede());

        MvcResult r = ejecutar(post("/radiologia/comisiones"), doctorClinica, derivacion(crearDerivador(admin), "Paciente Ajeno"));

        assertThat(r.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("Las comisiones de Villamax solo las ve quien trabaja en Villamax: ni Villadent ni su recepción")
    void comisionesAisladasDeVilladent() throws Exception {
        UsuarioPrueba admin = adminGeneral();
        MvcResult registrada = ejecutar(post("/radiologia/comisiones"), admin, derivacion(crearDerivador(admin), "Paciente Aislado"));
        assertThat(registrada.getResponse().getStatus()).isEqualTo(201);
        String id = leer(registrada).get("id").asText();

        assertThat(ids(ejecutar(get("/radiologia/comisiones").header("X-Negocio", "VILLAMAX"), admin))).contains(id);
        assertThat(ids(ejecutar(get("/radiologia/comisiones").header("X-Negocio", "VILLADENT"), admin))).doesNotContain(id);

        String derivadorCreadoId = crearDerivador(admin);
        assertThat(ids(ejecutar(get("/radiologia/derivadores").header("X-Negocio", "VILLAMAX"), admin))).contains(derivadorCreadoId);
        assertThat(ids(ejecutar(get("/radiologia/derivadores").header("X-Negocio", "VILLADENT"), admin))).isEmpty();

        JsonNode resumenVillamax = leer(ejecutar(get("/radiologia/comisiones/resumen").header("X-Negocio", "VILLAMAX"), admin));
        assertThat(resumenVillamax.get("cantidadPendientes").asLong()).isGreaterThanOrEqualTo(1);

        JsonNode resumenVilladent = leer(ejecutar(get("/radiologia/comisiones/resumen").header("X-Negocio", "VILLADENT"), admin));
        assertThat(resumenVilladent.get("cantidadPendientes").asLong()).isEqualTo(0);
        assertThat(resumenVilladent.get("totalMontoPendiente").decimalValue()).isEqualByComparingTo("0.00");

        UsuarioPrueba recepcionClinica = crearUsuario("RECEPCION", crearSede());
        assertThat(ids(ejecutar(get("/radiologia/derivadores"), recepcionClinica))).isEmpty();
        assertThat(ids(ejecutar(get("/radiologia/comisiones"), recepcionClinica))).doesNotContain(id);
        assertThat(ejecutar(get("/radiologia/comisiones/" + id), recepcionClinica).getResponse().getStatus()).isEqualTo(403);
        assertThat(ejecutar(patch("/radiologia/comisiones/" + id + "/liquidar"), recepcionClinica, Map.of("metodoPago", "YAPE"))
                .getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("La reserva pública rechaza combinaciones incoherentes entre servicio y sede de distinto negocio")
    void reservaPublicaRechazaCombinacionIncoherente() throws Exception {
        Map<String, Object> cuerpo = Map.of(
                "dni", "44556699",
                "nombres", "Juan",
                "apellidos", "Perez",
                "telefono", "987654321",
                "sedeId", SEDE_CHILCA_1,
                "fechaPreferencia", java.time.LocalDate.now().plusDays(2).toString(),
                "turno", "MANANA",
                "motivoConsulta", "vmx-panoramica"
        );
        org.springframework.test.web.servlet.MvcResult r = ejecutar(post("/reservas-publicas"), null, cuerpo);
        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        assertThat(r.getResponse().getContentAsString()).contains("incoherente");
    }

    @Test
    @DisplayName("Si el cobro que completa el estudio se registra después de atender la cita, la comisión nace con ese cobro y una sola vez")
    void comisionNaceAlCompletarElCobroDespuesDeAtender() throws Exception {
        UsuarioPrueba admin = adminGeneral();
        UsuarioPrueba recepcion = crearUsuario("RECEPCION", SEDE_VILLAMAX);
        String derivador = crearDerivador(admin);
        UUID paciente = insertarPaciente(SEDE_VILLAMAX, "Jorge", "Tello " + tag());
        UUID plan = insertarTratamiento(paciente, SEDE_VILLAMAX, "Radiografía panorámica", "50.00");
        UUID cita = insertarCita(paciente, SEDE_VILLAMAX, "ATENDIDA", java.time.LocalDate.now());
        jdbc.update("UPDATE citas SET paciente_nombre = 'Jorge Tello', tratamiento_id = ?, motivo_consulta = 'Radiografía panorámica', doctor_derivador_id = ?::uuid WHERE id = ?",
                plan, derivador, cita);
        // Otra prueba pudo dejar abierta la caja de Villamax: abrirla aquí es idempotente a efectos del cobro.
        ejecutar(post("/caja/abrir"), recepcion, Map.of("sedeId", SEDE_VILLAMAX, "montoApertura", 0));

        MvcResult parcial = ejecutar(post("/pagos"), recepcion, Map.of("pacienteId", paciente.toString(), "sedeId", SEDE_VILLAMAX,
                "tratamientoId", plan.toString(), "monto", 20, "metodoPago", "EFECTIVO", "comprobanteTipo", "RECIBO"));
        assertThat(parcial.getResponse().getStatus()).as(parcial.getResponse().getContentAsString()).isEqualTo(201);
        assertThat(contar("SELECT count(*) FROM comisiones_radiologicas WHERE cita_id = ?", cita)).isZero();

        MvcResult completo = ejecutar(post("/pagos"), recepcion, Map.of("pacienteId", paciente.toString(), "sedeId", SEDE_VILLAMAX,
                "tratamientoId", plan.toString(), "monto", 30, "metodoPago", "EFECTIVO", "comprobanteTipo", "RECIBO"));
        assertThat(completo.getResponse().getStatus()).as(completo.getResponse().getContentAsString()).isEqualTo(201);

        assertThat(contar("SELECT count(*) FROM comisiones_radiologicas WHERE cita_id = ? AND estado = 'PENDIENTE'", cita)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT monto_comision FROM comisiones_radiologicas WHERE cita_id = ?", BigDecimal.class, cita))
                .isEqualByComparingTo("10.00");
    }
}
