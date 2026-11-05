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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Presupuestos (piezas × costo por pieza, aceptación parcial) y cambio de sesiones con motivo, contra Postgres real. */
@Testcontainers(disabledWithoutDocker = true)
class PresupuestosYSesionesPostgresTest extends PacientesPostgresTestBase {

    private static final String SEDE = "sede-chilca-1";

    private UUID doctor(UsuarioPrueba admin) throws Exception {
        MvcResult r = ejecutar(get("/especialistas"), admin);
        JsonNode lista = leer(r);
        assertThat(lista.size()).isGreaterThan(0);
        return UUID.fromString(lista.get(0).get("id").asText());
    }

    private Map<String, Object> cuerpoPresupuesto(UUID paciente, UUID doctor) {
        return Map.of("pacienteId", paciente.toString(), "sedeId", SEDE, "especialistaId", doctor.toString(),
                "motivo", "Vino por una curación",
                "items", List.of(
                        Map.of("nombre", "Curación", "piezaFdi", 16, "gravedad", "MODERADO", "cantidad", 2, "precioUnitario", 80),
                        Map.of("nombre", "Endodoncia", "piezaFdi", 26, "gravedad", "SEVERO", "cantidad", 1, "precioUnitario", 350)));
    }

    @Test
    @DisplayName("El presupuesto suma piezas × costo por pieza; al aceptar solo una parte se crea el plan con esa parte")
    void presupuestoAceptadoParcialmente() throws Exception {
        UsuarioPrueba admin = adminGeneral();
        UUID paciente = insertarPaciente(SEDE, "Rosa", "Quispe " + tag());
        UUID doctor = doctor(admin);

        MvcResult creado = ejecutar(post("/presupuestos"), admin, cuerpoPresupuesto(paciente, doctor));
        assertThat(creado.getResponse().getStatus()).as(creado.getResponse().getContentAsString()).isEqualTo(201);
        JsonNode presupuesto = leer(creado);
        assertThat(presupuesto.get("total").decimalValue()).isEqualByComparingTo("510.00");
        assertThat(presupuesto.get("estado").asText()).isEqualTo("VIGENTE");
        String id = presupuesto.get("id").asText();
        String curacion = null;
        for (JsonNode item : presupuesto.get("items")) {
            if (item.get("nombre").asText().equals("Curación")) {
                curacion = item.get("id").asText();
                assertThat(item.get("subtotal").decimalValue()).isEqualByComparingTo("160.00");
            }
        }

        MvcResult aceptado = ejecutar(post("/presupuestos/" + id + "/aceptar"), admin, Map.of("itemIds", List.of(curacion), "totalSesiones", 2));
        assertThat(aceptado.getResponse().getStatus()).as(aceptado.getResponse().getContentAsString()).isEqualTo(200);
        JsonNode resultado = leer(aceptado);
        assertThat(resultado.get("estado").asText()).isEqualTo("ACEPTADO");
        assertThat(resultado.get("totalAceptado").decimalValue()).isEqualByComparingTo("160.00");
        String plan = resultado.get("tratamientoId").asText();

        Map<String, Object> fila = jdbc.queryForMap("SELECT costo_total, total_sesiones FROM tratamientos WHERE id = ?", UUID.fromString(plan));
        assertThat(((java.math.BigDecimal) fila.get("costo_total"))).isEqualByComparingTo("160.00");
        assertThat(fila.get("total_sesiones")).isEqualTo(2);
        Map<String, Object> item = jdbc.queryForMap("SELECT cantidad, pieza_fdi, gravedad, precio_base FROM tratamiento_items WHERE tratamiento_id = ?", UUID.fromString(plan));
        assertThat(item.get("cantidad")).isEqualTo(2);
        assertThat(item.get("pieza_fdi")).isEqualTo(16);
        assertThat(item.get("gravedad")).isEqualTo("MODERADO");

        // Ya resuelto: no se acepta ni se rechaza otra vez.
        assertThat(ejecutar(post("/presupuestos/" + id + "/aceptar"), admin, Map.of()).getResponse().getStatus()).isEqualTo(409);
        assertThat(ejecutar(post("/presupuestos/" + id + "/rechazar"), admin).getResponse().getStatus()).isEqualTo(409);

        JsonNode lista = leer(ejecutar(get("/presupuestos/paciente/" + paciente), admin));
        assertThat(lista).hasSize(1);
    }

    @Test
    @DisplayName("Un presupuesto rechazado queda registrado y no crea plan")
    void presupuestoRechazado() throws Exception {
        UsuarioPrueba admin = adminGeneral();
        UUID paciente = insertarPaciente(SEDE, "Luis", "Meza " + tag());
        UUID doctor = doctor(admin);
        String id = leer(ejecutar(post("/presupuestos"), admin, cuerpoPresupuesto(paciente, doctor))).get("id").asText();

        MvcResult rechazado = ejecutar(post("/presupuestos/" + id + "/rechazar"), admin);
        assertThat(leer(rechazado).get("estado").asText()).isEqualTo("RECHAZADO");
        assertThat(contar("SELECT count(*) FROM tratamientos WHERE paciente_id = ?", paciente)).isZero();
    }

    @Test
    @DisplayName("Los presupuestos pendientes de una sede solo incluyen los vigentes, con el nombre del paciente")
    void presupuestosPendientes() throws Exception {
        UsuarioPrueba admin = adminGeneral();
        String marcaNombre = "Pend" + tag();
        UUID vigente = insertarPaciente(SEDE, "Ana", marcaNombre);
        UUID rechazado = insertarPaciente(SEDE, "Beto", marcaNombre);
        UUID doctor = doctor(admin);
        String idVigente = leer(ejecutar(post("/presupuestos"), admin, cuerpoPresupuesto(vigente, doctor))).get("id").asText();
        String idRechazado = leer(ejecutar(post("/presupuestos"), admin, cuerpoPresupuesto(rechazado, doctor))).get("id").asText();
        ejecutar(post("/presupuestos/" + idRechazado + "/rechazar"), admin);

        MvcResult r = ejecutar(get("/presupuestos/pendientes").param("sedeId", SEDE), admin);
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        JsonNode lista = leer(r);
        assertThat(ids(lista)).contains(idVigente).doesNotContain(idRechazado);
        for (JsonNode p : lista) {
            if (p.get("id").asText().equals(idVigente)) {
                assertThat(p.get("pacienteNombre").asText()).isEqualTo("Ana " + marcaNombre);
                assertThat(p.get("total").decimalValue()).isEqualByComparingTo("510.00");
                assertThat(p.get("lineas").asInt()).isEqualTo(2);
            }
        }
    }

    @Test
    @DisplayName("Cambiar las sesiones del plan exige motivo, queda registrado y se ve en el plan")
    void cambiarSesionesConMotivo() throws Exception {
        UsuarioPrueba admin = adminGeneral();
        UUID paciente = insertarPaciente(SEDE, "Carlos", "Huamán " + tag());
        UUID plan = insertarTratamiento(paciente, SEDE, "Endodoncia", "400.00");

        MvcResult sinMotivo = ejecutar(patch("/tratamientos/" + plan), admin, Map.of("totalSesiones", 3));
        assertThat(sinMotivo.getResponse().getStatus()).isEqualTo(400);

        MvcResult cambiado = ejecutar(patch("/tratamientos/" + plan), admin, Map.of("totalSesiones", 3, "motivoCambioSesiones", "Conducto más complejo de lo previsto"));
        assertThat(cambiado.getResponse().getStatus()).as(cambiado.getResponse().getContentAsString()).isEqualTo(200);
        JsonNode dto = leer(cambiado);
        assertThat(dto.get("totalSesiones").asInt()).isEqualTo(3);
        assertThat(dto.get("cambiosSesiones")).hasSize(1);
        assertThat(dto.get("cambiosSesiones").get(0).get("motivo").asText()).contains("Conducto más complejo");
        assertThat(contar("SELECT count(*) FROM tratamiento_sesiones_cambios WHERE tratamiento_id = ?", plan)).isEqualTo(1);
    }
}
