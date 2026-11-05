package com.odontomax.pacientes.caracterizacion;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Consumo de insumos por procedimiento: la receta del servicio, el descuento al atender la cita, la idempotencia
 * y la alerta cuando el stock no alcanza.
 */
@Testcontainers(disabledWithoutDocker = true)
class ConsumoInsumosPostgresTest extends PacientesPostgresTestBase {

    @org.springframework.beans.factory.annotation.Autowired
    private com.odontomax.inventario.api.ConsumoInsumosModuloApi consumo;

    private String servicio(String unidad) {
        String id = "svc-" + tag();
        jdbc.update("INSERT INTO servicios_web (id, nombre, especialidad, duracion_min, unidad_de_negocio) VALUES (?, ?, 'Odontología general', 30, ?)",
                id, "Restauración " + id, unidad);
        return id;
    }

    private String nombreServicio(String id) {
        return jdbc.queryForObject("SELECT nombre FROM servicios_web WHERE id = ?", String.class, id);
    }

    private UUID insumo(String sede, int stock) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO productos_inventario (id, codigo, nombre, unidad) VALUES (?, ?, ?, 'unidad')", id, "INS-" + tag(), "Resina " + tag());
        if (stock > 0) {
            jdbc.update("INSERT INTO inventario_movimientos (producto_id, sede_id, tipo, cantidad) VALUES (?, ?, 'ENTRADA', ?)", id, sede, stock);
        }
        return id;
    }

    private int stock(UUID producto, String sede) {
        Integer s = jdbc.query("SELECT cantidad FROM inventario_stock WHERE producto_id = ? AND sede_id = ?",
                rs -> rs.next() ? rs.getInt(1) : 0, producto, sede);
        return s == null ? 0 : s;
    }

    private UUID citaEnAtencion(String sede, String motivo) {
        UUID paciente = insertarPaciente(sede, "Rosa", "Quispe " + tag());
        UUID cita = insertarCita(paciente, sede, "EN_ATENCION", LocalDate.now().plusDays(1));
        jdbc.update("UPDATE citas SET motivo_consulta = ? WHERE id = ?", motivo, cita);
        return cita;
    }

    private MvcResult atender(UsuarioPrueba u, UUID cita) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.patch("/citas/" + cita + "/estado")
                .header("Authorization", u.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"ATENDIDA\"}")).andReturn();
    }

    private void receta(String servicio, UUID producto, int cantidad) throws Exception {
        MvcResult r = ejecutar(put("/inventario/servicios/" + servicio + "/insumos").contentType(MediaType.APPLICATION_JSON),
                adminGeneral(), java.util.List.of(java.util.Map.of("productoId", producto.toString(), "cantidad", cantidad)));
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(200);
    }

    @Test
    @DisplayName("Al atender la cita se descuenta la receta del servicio con un movimiento ligado a la cita, y una segunda vez no descuenta")
    void descuentaUnaSolaVez() throws Exception {
        String sede = crearSede();
        UsuarioPrueba admin = adminGeneral();
        String servicio = servicio("VILLADENT");
        UUID resina = insumo(sede, 10);
        receta(servicio, resina, 3);
        UUID cita = citaEnAtencion(sede, nombreServicio(servicio));

        assertThat(atender(admin, cita).getResponse().getStatus()).isEqualTo(200);

        assertThat(stock(resina, sede)).isEqualTo(7);
        assertThat(contar("SELECT count(*) FROM inventario_movimientos WHERE cita_id = ? AND tipo = 'SALIDA' AND cantidad = -3", cita)).isEqualTo(1);

        // Aunque el descuento se pida otra vez para la misma cita, no se repite.
        consumo.descontarPorAtencion(cita, sede, nombreServicio(servicio));
        assertThat(stock(resina, sede)).isEqualTo(7);

        // Un segundo intento (la cita ya está atendida) no vuelve a descontar.
        assertThat(atender(admin, cita).getResponse().getStatus()).isEqualTo(409);
        assertThat(stock(resina, sede)).isEqualTo(7);
        assertThat(contar("SELECT count(*) FROM consumos_insumos WHERE cita_id = ?", cita)).isEqualTo(1);
    }

    @Test
    @DisplayName("Si falta stock no bloquea la atención: descuenta lo que hay y deja una alerta con el faltante")
    void alertaPorFaltante() throws Exception {
        String sede = crearSede();
        UsuarioPrueba admin = adminGeneral();
        String servicio = servicio("VILLADENT");
        UUID anestesia = insumo(sede, 2);
        receta(servicio, anestesia, 5);
        UUID cita = citaEnAtencion(sede, nombreServicio(servicio));

        assertThat(atender(admin, cita).getResponse().getStatus()).isEqualTo(200);

        assertThat(stock(anestesia, sede)).isZero();
        JsonNode alertas = leer(ejecutar(get("/inventario/alertas-consumo").param("sedeId", sede), admin));
        assertThat(alertas).hasSize(1);
        assertThat(alertas.get(0).get("faltante").asInt()).isEqualTo(3);
        assertThat(alertas.get(0).get("cantidadDescontada").asInt()).isEqualTo(2);

        String alerta = alertas.get(0).get("id").asText();
        assertThat(ejecutar(post("/inventario/alertas-consumo/" + alerta + "/resolver"), admin).getResponse().getStatus()).isEqualTo(204);
        assertThat(leer(ejecutar(get("/inventario/alertas-consumo").param("sedeId", sede), admin))).isEmpty();
    }

    @Test
    @DisplayName("Un servicio sin receta o de Villamax no descuenta nada")
    void sinRecetaOVillamax() throws Exception {
        String sede = crearSede();
        UsuarioPrueba admin = adminGeneral();
        String sinReceta = servicio("VILLADENT");
        String villamax = servicio("VILLAMAX");
        UUID resina = insumo(sede, 10);

        UUID citaSinReceta = citaEnAtencion(sede, nombreServicio(sinReceta));
        UUID citaVillamax = citaEnAtencion(sede, nombreServicio(villamax));
        jdbc.update("INSERT INTO servicio_insumos (servicio_id, producto_id, cantidad) VALUES (?, ?, 4)", villamax, resina);

        assertThat(atender(admin, citaSinReceta).getResponse().getStatus()).isEqualTo(200);
        assertThat(atender(admin, citaVillamax).getResponse().getStatus()).isEqualTo(200);

        assertThat(stock(resina, sede)).isEqualTo(10);
        assertThat(contar("SELECT count(*) FROM consumos_insumos WHERE sede_id = ?", sede)).isZero();
    }

    @Test
    @DisplayName("La receta no acepta insumos repetidos ni servicios de Villamax, y solo la edita el administrador general")
    void validacionesDeLaReceta() throws Exception {
        String sede = crearSede();
        String villadent = servicio("VILLADENT");
        String villamax = servicio("VILLAMAX");
        UUID resina = insumo(sede, 1);
        var repetido = java.util.List.of(java.util.Map.of("productoId", resina.toString(), "cantidad", 1),
                java.util.Map.of("productoId", resina.toString(), "cantidad", 2));
        var unaLinea = java.util.List.of(java.util.Map.of("productoId", resina.toString(), "cantidad", 1));

        assertThat(ejecutar(put("/inventario/servicios/" + villadent + "/insumos").contentType(MediaType.APPLICATION_JSON), adminGeneral(), repetido)
                .getResponse().getStatus()).isEqualTo(400);
        assertThat(ejecutar(put("/inventario/servicios/" + villamax + "/insumos").contentType(MediaType.APPLICATION_JSON), adminGeneral(), unaLinea)
                .getResponse().getStatus()).isEqualTo(404);
        assertThat(ejecutar(put("/inventario/servicios/" + villadent + "/insumos").contentType(MediaType.APPLICATION_JSON),
                crearUsuario("ADMIN_SEDE", sede), unaLinea).getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("El administrador de otra sede no ve las alertas de consumo de esta sede")
    void alertasPorSede() throws Exception {
        String sede = crearSede();
        UsuarioPrueba otraSede = crearUsuario("ADMIN_SEDE", crearSede());

        assertThat(ejecutar(get("/inventario/alertas-consumo").param("sedeId", sede), otraSede).getResponse().getStatus()).isEqualTo(403);
    }
}
