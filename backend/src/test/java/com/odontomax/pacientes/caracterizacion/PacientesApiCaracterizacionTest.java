package com.odontomax.pacientes.caracterizacion;

import com.fasterxml.jackson.databind.JsonNode;
import com.odontomax.agenda.application.port.out.PacienteReservaPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Protege los consumidores del modulo (pasos P1-P3): reserva publica de agenda (PacienteReservaPort), bandeja de
 * cumpleanos, nombres en citas, tratamientos, pagos, laboratorio, odontograma PDF y reportes frente a pacientes.
 */
@Testcontainers(disabledWithoutDocker = true)
class PacientesApiCaracterizacionTest extends PacientesPostgresTestBase {

    @Autowired
    private PacienteReservaPort pacienteReservaPort;

    private int ipSeq = 1;

    private Map<String, Object> reserva(String dni, String sede, String telefono) {
        Map<String, Object> m = new HashMap<>();
        m.put("dni", dni);
        m.put("nombres", "  Rosa  ");
        m.put("apellidos", " Flores ");
        m.put("telefono", telefono);
        m.put("motivoConsulta", "Limpieza");
        m.put("sedeId", sede);
        m.put("fechaPreferencia", LocalDate.now().plusDays(20).toString());
        m.put("turno", "MANANA");
        return m;
    }

    private static String telefonoUnico() {
        return "9" + String.valueOf(10_000_000 + (int) (Math.random() * 89_999_999));
    }

    private MvcResult reservar(Map<String, Object> body) throws Exception {
        return ejecutar(post("/reservas-publicas"), null, "10.55." + (ipSeq++) + "." + (int) (Math.random() * 250), body);
    }

    // ------------------------------------------------------------------ reserva publica

    @Test
    @DisplayName("Reserva publica con DNI nuevo: crea el paciente (recortado, activo, sin saldo, sin consentimientos) y la cita con el nombre 'nombres apellidos'")
    void reservaCreaPaciente() throws Exception {
        String sede = crearSede();
        String dni = dniUnico();
        String tel = telefonoUnico();

        MvcResult r = reservar(reserva(dni, sede, tel));

        assertThat(r.getResponse().getStatus()).isEqualTo(201);
        Map<String, Object> p = jdbc.queryForMap("SELECT * FROM pacientes WHERE dni = ?", dni);
        assertThat(p.get("nombres")).isEqualTo("Rosa");
        assertThat(p.get("apellidos")).isEqualTo("Flores");
        assertThat(p.get("telefono")).isEqualTo(tel);
        assertThat(p.get("sede_id")).isEqualTo(sede);
        assertThat(p.get("archivado")).isEqualTo(false);
        assertThat(p.get("saldo_pendiente").toString()).isEqualTo("0.00");
        assertThat(p.get("consentimiento_promociones")).isEqualTo(false);
        assertThat(p.get("consentimiento_datos_en")).isNull();
        assertThat(p.get("total_inasistencias")).isEqualTo(0);
        Map<String, Object> cita = jdbc.queryForMap("SELECT paciente_nombre, paciente_telefono, estado FROM citas WHERE paciente_id = ?", p.get("id"));
        assertThat(cita.get("paciente_nombre")).isEqualTo("Rosa Flores");
        assertThat(cita.get("paciente_telefono")).isEqualTo(tel);
    }

    @Test
    @DisplayName("Reserva sobre un paciente existente: sin telefono se rellena y se guarda (B-043: consentimiento_datos_en se conserva); con telefono no se toca nada")
    void reservaSobreExistente() throws Exception {
        String sede = crearSede();
        UUID sinTelefono = insertarPaciente(sede, "Original", "SinTel");
        UUID conTelefono = insertarPaciente(sede, "Original", "ConTel");
        jdbc.update("UPDATE pacientes SET consentimiento_datos_en = now(), alergias = 'Polen' WHERE id IN (?, ?)", sinTelefono, conTelefono);
        jdbc.update("UPDATE pacientes SET telefono = '911000222' WHERE id = ?", conTelefono);
        String tel = telefonoUnico();

        assertThat(reservar(reserva((String) pacienteBd(sinTelefono).get("dni"), sede, tel)).getResponse().getStatus()).isEqualTo(201);
        Map<String, Object> a = pacienteBd(sinTelefono);
        assertThat(a.get("telefono")).isEqualTo(tel);
        assertThat(a.get("nombres")).as("los datos existentes no se pisan").isEqualTo("Original");
        assertThat(a.get("alergias")).isEqualTo("Polen");
        assertThat(a.get("consentimiento_datos_en")).as("B-043 por la ruta de reserva").isNotNull();
        Map<String, Object> cita = jdbc.queryForMap("SELECT paciente_nombre FROM citas WHERE paciente_id = ?", sinTelefono);
        assertThat(cita.get("paciente_nombre")).isEqualTo("Original SinTel");

        assertThat(reservar(reserva((String) pacienteBd(conTelefono).get("dni"), sede, telefonoUnico())).getResponse().getStatus()).isEqualTo(201);
        Map<String, Object> b = pacienteBd(conTelefono);
        assertThat(b.get("telefono")).isEqualTo("911000222");
        assertThat(b.get("consentimiento_datos_en")).isNotNull();
    }

    @Test
    @DisplayName("Reserva sobre un paciente archivado: se reutiliza (sigue archivado) y no se crea otro")
    void reservaSobreArchivado() throws Exception {
        String sede = crearSede();
        UUID id = insertarPaciente(sede, "Archivado", "Reserva");
        jdbc.update("UPDATE pacientes SET archivado = true WHERE id = ?", id);

        assertThat(reservar(reserva((String) pacienteBd(id).get("dni"), sede, telefonoUnico())).getResponse().getStatus()).isEqualTo(201);

        assertThat(pacienteBd(id).get("archivado")).isEqualTo(true);
        assertThat(contar("SELECT count(*) FROM citas WHERE paciente_id = ?", id)).isEqualTo(1);
    }

    @Test
    @DisplayName("PacienteReservaPort (agenda): obtenerOCrearPaciente, findById y cumpleaneros devuelven el nombre 'nombres apellidos' del dominio")
    void puertoDeReserva() {
        String sede = crearSede();
        String dni = dniUnico();
        PacienteReservaPort.PacienteReservaInfo info = pacienteReservaPort.obtenerOCrearPaciente(" " + dni + " ", " Luis ", " Rojas ", " 987000111 ", sede);
        assertThat(info.dni()).isEqualTo(dni);
        assertThat(info.nombreCompleto()).isEqualTo("Luis Rojas");
        assertThat(info.telefono()).isEqualTo("987000111");
        assertThat(pacienteReservaPort.obtenerOCrearPaciente(dni, "Otro", "Nombre", "900000000", sede).id()).isEqualTo(info.id());
        assertThat(pacienteReservaPort.findById(info.id())).isPresent();
        assertThat(pacienteReservaPort.findById(UUID.randomUUID())).isEmpty();

        LocalDate nac = LocalDate.of(1990, 3, 14);
        jdbc.update("UPDATE pacientes SET fecha_nacimiento = ?, consentimiento_promociones = true, total_inasistencias = 2 WHERE id = ?", nac, info.id());
        var cumple = pacienteReservaPort.obtenerCumpleanerosDelDia(sede, 3, 14);
        assertThat(cumple).hasSize(1);
        assertThat(cumple.get(0).totalInasistencias()).isEqualTo(2);
        assertThat(pacienteReservaPort.obtenerCumpleanerosDelDia(sede, 3, 15)).isEmpty();
    }

    // ------------------------------------------------------------------ bandeja de cumpleanos

    @Test
    @DisplayName("Bandeja de tareas: SALUDO_CUMPLEANOS solo para activos de la sede con consentimiento de promociones y cumpleanos ese dia; B-046: el aviso depende de total_inasistencias")
    void bandejaDeCumpleanos() throws Exception {
        String sede = crearSede();
        UsuarioPrueba recepcion = crearUsuario("RECEPCION", sede);
        LocalDate dia = LocalDate.of(2030, 5, 17);
        UUID cumple = insertarPaciente(sede, "Cumple", "Hoy");
        UUID sinConsentimiento = insertarPaciente(sede, "Sin", "Consentimiento");
        UUID archivado = insertarPaciente(sede, "Archivado", "Cumple");
        UUID otroDia = insertarPaciente(sede, "Otro", "Dia");
        UUID otraSede = insertarPaciente(crearSede(), "Otra", "Sede");
        for (UUID id : new UUID[]{cumple, sinConsentimiento, archivado, otraSede}) {
            jdbc.update("UPDATE pacientes SET fecha_nacimiento = '1990-05-17' WHERE id = ?", id);
        }
        jdbc.update("UPDATE pacientes SET fecha_nacimiento = '1990-05-18', consentimiento_promociones = true WHERE id = ?", otroDia);
        jdbc.update("UPDATE pacientes SET consentimiento_promociones = true, total_inasistencias = 2, telefono = '912345678' WHERE id = ?", cumple);
        jdbc.update("UPDATE pacientes SET consentimiento_promociones = true, archivado = true WHERE id = ?", archivado);
        jdbc.update("UPDATE pacientes SET consentimiento_promociones = true WHERE id = ?", otraSede);

        MvcResult r = ejecutar(get("/citas/bandeja-tareas").param("fecha", dia.toString()), recepcion);

        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        JsonNode tareas = leer(r).get("tareas");
        JsonNode item = null;
        int cumples = 0;
        for (JsonNode t : tareas) {
            if ("SALUDO_CUMPLEANOS".equals(t.get("tipo").asText())) {
                cumples++;
                item = t;
            }
        }
        assertThat(cumples).isEqualTo(1);
        assertThat(item.get("id").asText()).isEqualTo("CUMPLE-" + cumple);
        assertThat(item.get("pacienteId").asText()).isEqualTo(cumple.toString());
        assertThat(item.get("pacienteNombre").asText()).isEqualTo("Cumple Hoy");
        assertThat(item.get("pacienteDni").asText()).isEqualTo(pacienteBd(cumple).get("dni"));
        assertThat(item.get("pacienteTelefono").asText()).isEqualTo("912345678");
        assertThat(item.get("totalInasistencias").asInt()).isEqualTo(2);
        assertThat(item.get("avisoInasistencias").asBoolean()).isTrue();
        assertThat(leer(r).get("totalCumpleanosPendientes").asInt()).isEqualTo(1);
    }

    // ------------------------------------------------------------------ otros modulos

    @Test
    @DisplayName("Tratamientos y pagos frente a pacientes: mensajes exactos para inexistente, 403 de otra sede y listas vacias")
    void tratamientosYPagos() throws Exception {
        String sede = crearSede();
        String otra = crearSede();
        UsuarioPrueba admin = crearUsuario("ADMIN_SEDE", sede);
        UUID propio = insertarPaciente(sede, "Propio", "Modulos");
        UUID ajeno = insertarPaciente(otra, "Ajeno", "Modulos");
        UUID inexistente = UUID.randomUUID();

        MvcResult t = ejecutar(get("/tratamientos/paciente/" + inexistente), admin);
        assertThat(t.getResponse().getStatus()).isEqualTo(400);
        assertThat(leer(t).get("message").asText()).isEqualTo("No se encontró el paciente con ID: " + inexistente);
        MvcResult p = ejecutar(get("/pagos/paciente/" + inexistente), admin);
        assertThat(p.getResponse().getStatus()).isEqualTo(400);
        assertThat(leer(p).get("message").asText()).isEqualTo("Paciente no encontrado");

        assertThat(ejecutar(get("/tratamientos/paciente/" + ajeno), admin).getResponse().getStatus()).isEqualTo(403);
        assertThat(ejecutar(get("/pagos/paciente/" + ajeno), admin).getResponse().getStatus()).isEqualTo(403);
        assertThat(leer(ejecutar(get("/tratamientos/paciente/" + propio), admin))).isEmpty();
        assertThat(leer(ejecutar(get("/pagos/paciente/" + propio), admin))).isEmpty();

        Map<String, Object> trat = new HashMap<>();
        trat.put("pacienteId", inexistente.toString());
        trat.put("sedeId", sede);
        trat.put("especialistaId", UUID.randomUUID().toString());
        trat.put("nombre", "Plan");
        trat.put("costoTotal", 100);
        MvcResult creado = ejecutar(post("/tratamientos"), admin, trat);
        assertThat(creado.getResponse().getStatus()).isEqualTo(400);
        assertThat(leer(creado).get("message").asText()).isEqualTo("Paciente no encontrado con ID: " + inexistente);

        Map<String, Object> pago = new HashMap<>();
        pago.put("pacienteId", inexistente.toString());
        pago.put("sedeId", sede);
        pago.put("monto", 10);
        pago.put("metodoPago", "EFECTIVO");
        MvcResult pagado = ejecutar(post("/pagos"), admin, pago);
        assertThat(pagado.getResponse().getStatus()).isEqualTo(400);
        assertThat(leer(pagado).get("message").asText()).isEqualTo("Paciente no encontrado con ID: " + inexistente);
    }

    @Test
    @DisplayName("Laboratorio: paciente inexistente 404 'Paciente no encontrado'; las ordenes muestran 'nombres apellidos' y DNI del paciente")
    void laboratorio() throws Exception {
        String sede = crearSede();
        UsuarioPrueba u = crearUsuario("RECEPCION", sede);
        UUID id = insertarPaciente(sede, "Lab", "Paciente");
        UUID orden = insertarOrdenLaboratorio(id, sede);
        Map<String, Object> body = new HashMap<>();
        body.put("pacienteId", UUID.randomUUID().toString());
        body.put("especialistaId", UUID.randomUUID().toString());
        body.put("sedeId", sede);
        body.put("laboratorioId", UUID.randomUUID().toString());
        body.put("tipoTrabajo", "CORONA");

        MvcResult nuevo = ejecutar(post("/laboratorio/ordenes"), u, body);
        assertThat(nuevo.getResponse().getStatus()).isEqualTo(404);
        assertThat(leer(nuevo).get("message").asText()).isEqualTo("Paciente no encontrado");

        MvcResult detalle = ejecutar(get("/laboratorio/ordenes/" + orden), u);
        assertThat(detalle.getResponse().getStatus()).isEqualTo(200);
        assertThat(leer(detalle).get("pacienteNombre").asText()).isEqualTo("Lab Paciente");
        assertThat(leer(detalle).get("pacienteDni").asText()).isEqualTo(pacienteBd(id).get("dni"));
        JsonNode lista = leer(ejecutar(get("/laboratorio/ordenes").param("pacienteId", id.toString()), u));
        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).get("pacienteNombre").asText()).isEqualTo("Lab Paciente");
    }

    @Test
    @DisplayName("Odontograma PDF: paciente inexistente 400 'Paciente no encontrado: id'; existente 200 con un PDF")
    void odontogramaPdf() throws Exception {
        String sede = crearSede();
        UsuarioPrueba u = crearUsuario("ODONTOLOGO", sede);
        UUID id = insertarPaciente(sede, "Pdf", "Odontograma");
        vincularCita(id, u);
        UUID inexistente = UUID.randomUUID();

        MvcResult no = ejecutar(get("/odontograma/pacientes/" + inexistente + "/pdf"), u);
        assertThat(no.getResponse().getStatus()).isEqualTo(400);
        assertThat(leer(no).get("message").asText()).isEqualTo("Paciente no encontrado: " + inexistente);

        MvcResult ok = ejecutar(get("/odontograma/pacientes/" + id + "/pdf"), u);
        assertThat(ok.getResponse().getStatus()).isEqualTo(200);
        assertThat(new String(ok.getResponse().getContentAsByteArray(), 0, 4, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF");
    }

    // ------------------------------------------------------------------ reportes

    @Test
    @DisplayName("Reporte de pacientes: totales de activos, fuentes de captacion, cumpleanos proximos, nuevos por creado_en e 'inactivos' inventado (total/10, B-046)")
    void reportePacientes() throws Exception {
        String sede = crearSede();
        UsuarioPrueba admin = crearUsuario("ADMIN_SEDE", sede);
        LocalDate hoy = LocalDate.now();
        UUID[] ids = new UUID[12];
        for (int i = 0; i < 12; i++) {
            ids[i] = insertarPaciente(sede, "Rep" + i, "Orte");
        }
        jdbc.update("UPDATE pacientes SET fuente_captacion = 'FACEBOOK' WHERE id IN (?, ?)", ids[0], ids[1]);
        jdbc.update("UPDATE pacientes SET archivado = true WHERE id = ?", ids[11]);
        // creado_en a mediodia UTC de hoy: el reporte usa toLocalDate() sin zona y cerca de medianoche now() cruzaria de dia
        jdbc.update("UPDATE pacientes SET creado_en = ? WHERE sede_id = ?", java.time.OffsetDateTime.of(hoy, java.time.LocalTime.NOON, java.time.ZoneOffset.UTC), sede);
        jdbc.update("UPDATE pacientes SET creado_en = now() - interval '400 days' WHERE id IN (?, ?, ?)", ids[2], ids[3], ids[4]);
        jdbc.update("UPDATE pacientes SET fecha_nacimiento = ? WHERE id = ?", hoy.minusYears(30).plusDays(5), ids[5]);
        jdbc.update("UPDATE pacientes SET fecha_nacimiento = ? WHERE id = ?", hoy.minusYears(30).plusDays(100), ids[6]);

        MvcResult r = ejecutar(get("/reportes/pacientes").param("sedeId", sede), admin);

        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        JsonNode j = leer(r);
        assertThat(j.get("sedeId").asText()).isEqualTo(sede);
        assertThat(j.get("totalPacientes").asInt()).as("sin archivados").isEqualTo(11);
        assertThat(j.get("pacientesNuevos").asInt()).isEqualTo(8);
        assertThat(j.get("pacientesRecurrentes").asInt()).isEqualTo(3);
        assertThat(j.get("pacientesInactivos").asInt()).as("total/10 inventado").isEqualTo(1);
        JsonNode fuentes = j.get("fuentesCaptacion");
        assertThat(fuentes.get(0).get("fuente").asText()).isEqualTo("Directo / No especificado");
        assertThat(fuentes.get(0).get("cantidad").asInt()).isEqualTo(9);
        assertThat(fuentes.get(1).get("fuente").asText()).isEqualTo("FACEBOOK");
        assertThat(fuentes.get(1).get("cantidad").asInt()).isEqualTo(2);
        JsonNode cumples = j.get("proximosCumpleanos");
        assertThat(ids(cumples)).containsExactly(ids[5].toString());
        assertThat(cumples.get(0).get("nombreCompleto").asText()).isEqualTo("Rep5 Orte");
        assertThat(ejecutar(get("/reportes/pacientes"), crearUsuario("RECEPCION", sede)).getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("Reporte de deudores: toma pacientes activos de la sede, nombre 'nombres apellidos' y saldo = tratamientos - pagos")
    void reporteDeudores() throws Exception {
        String sede = crearSede();
        UsuarioPrueba admin = crearUsuario("ADMIN_SEDE", sede);
        UUID deudor = insertarPaciente(sede, "Deudor", "Reporte");
        UUID archivado = insertarPaciente(sede, "Archivado", "Deudor");
        UUID t = insertarTratamiento(deudor, sede, "Plan", "300.00");
        insertarPago(deudor, sede, "100.00", t);
        insertarTratamiento(archivado, sede, "Plan", "50.00");
        jdbc.update("UPDATE pacientes SET archivado = true WHERE id = ?", archivado);

        JsonNode j = leer(ejecutar(get("/reportes/deudores").param("sedeId", sede), admin));

        assertThat(j.get("totalPacientesDeudores").asInt()).isEqualTo(1);
        JsonNode d = j.get("pacientes").get(0);
        assertThat(d.get("pacienteId").asText()).isEqualTo(deudor.toString());
        assertThat(d.get("pacienteNombre").asText()).isEqualTo("Deudor Reporte");
        assertThat(d.get("saldoPendiente").decimalValue()).isEqualByComparingTo("200");
        assertThat(d.get("dni").asText()).isEqualTo(pacienteBd(deudor).get("dni"));
    }

    @Test
    @DisplayName("Nombres de cita: al agendar sin nombre ni telefono se rellenan desde el paciente ('nombres apellidos', telefono)")
    void citaCompletaNombres() throws Exception {
        String sede = crearSede();
        UUID id = insertarPaciente(sede, "Nombre", "EnCita");
        jdbc.update("UPDATE pacientes SET telefono = '933222111' WHERE id = ?", id);
        Map<String, Object> body = new HashMap<>();
        body.put("pacienteId", id.toString());
        body.put("sedeId", sede);
        body.put("fecha", LocalDate.now().plusDays(30).toString());
        body.put("hora", "10:00");
        body.put("motivoConsulta", "Control");

        MvcResult r = ejecutar(post("/citas"), crearUsuario("RECEPCION", sede), body);

        assertThat(r.getResponse().getStatus()).isEqualTo(201);
        assertThat(leer(r).get("pacienteNombre").asText()).isEqualTo("Nombre EnCita");
        assertThat(leer(r).get("pacienteTelefono").asText()).isEqualTo("933222111");
    }
}
