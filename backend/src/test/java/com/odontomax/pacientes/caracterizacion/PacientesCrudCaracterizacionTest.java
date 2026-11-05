package com.odontomax.pacientes.caracterizacion;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Caracterizacion de alta, edicion y lectura por id de pacientes (POST/PUT/GET /pacientes), tal como se
 * comportan hoy. Congela, entre otros, los hallazgos B-043 (consentimiento_datos_en no se pierde al editar, corregido) y B-045
 * (id inexistente = 400).
 */
@Testcontainers(disabledWithoutDocker = true)
class PacientesCrudCaracterizacionTest extends PacientesPostgresTestBase {

    private static final String MENSAJE_INTEGRIDAD = "La operación hace referencia a un registro inexistente "
            + "(por ejemplo, una sede no registrada) o duplica uno existente.";

    private MvcResult crear(UsuarioPrueba u, Map<String, Object> cuerpo) throws Exception {
        return ejecutar(post("/pacientes"), u, cuerpo);
    }

    private MvcResult editar(UsuarioPrueba u, UUID id, Map<String, Object> cuerpo) throws Exception {
        return ejecutar(put("/pacientes/" + id), u, cuerpo);
    }

    // ------------------------------------------------------------------ alta

    @Test
    @DisplayName("Alta: 201 con las 29 claves exactas de PacienteResponseDTO, recorte de nombres, saldo 0, no archivado y 'HC-'+dni")
    void altaCompleta() throws Exception {
        String sede = crearSede();
        UsuarioPrueba admin = adminGeneral();
        String dni = dniUnico();
        Map<String, Object> body = new HashMap<>();
        body.put("dni", dni);
        body.put("nombres", "  Ana María  ");
        body.put("apellidos", "  Pérez Soto ");
        body.put("fechaNacimiento", "1990-05-17");
        body.put("telefono", "987654321");
        body.put("email", "ana@correo.pe");
        body.put("sedeId", sede);
        body.put("genero", "FEMENINO");
        body.put("fuenteCaptacion", "INSTAGRAM");
        body.put("doctorReferidor", "Dr. Ruiz");
        body.put("referidoPorPacienteNombre", "Luis");
        body.put("consentimientoPromociones", true);
        body.put("alergias", List.of("Polen", "Latex"));
        body.put("antecedentesMedicos", "Hipertensión");
        body.put("gestante", true);
        body.put("semanasGestacion", 12);

        MvcResult r = crear(admin, body);

        assertThat(r.getResponse().getStatus()).isEqualTo(201);
        JsonNode j = leer(r);
        assertThat(campos(j)).containsExactlyInAnyOrderElementsOf(CLAVES_PACIENTE);
        assertThat(j.get("id").asText()).isNotBlank();
        assertThat(j.get("dni").asText()).isEqualTo(dni);
        assertThat(j.get("nombres").asText()).isEqualTo("Ana María");
        assertThat(j.get("apellidos").asText()).isEqualTo("Pérez Soto");
        assertThat(j.get("nombreCompleto").asText()).isEqualTo("Ana María Pérez Soto");
        assertThat(j.get("fechaNacimiento").asText()).isEqualTo("1990-05-17");
        assertThat(j.get("edad").asInt()).isEqualTo(Period.between(LocalDate.of(1990, 5, 17), LocalDate.now()).getYears());
        assertThat(j.get("esCumpleaneroMes").asBoolean()).isEqualTo(LocalDate.now().getMonthValue() == 5);
        assertThat(j.get("telefono").asText()).isEqualTo("987654321");
        assertThat(j.get("email").asText()).isEqualTo("ana@correo.pe");
        assertThat(j.get("sedeId").asText()).isEqualTo(sede);
        assertThat(j.get("genero").asText()).isEqualTo("FEMENINO");
        assertThat(j.get("fuenteCaptacion").asText()).isEqualTo("INSTAGRAM");
        assertThat(j.get("doctorReferidor").asText()).isEqualTo("Dr. Ruiz");
        assertThat(j.get("referidoPorPacienteId").isNull()).isTrue();
        assertThat(j.get("referidoPorPacienteNombre").asText()).isEqualTo("Luis");
        assertThat(j.get("consentimientoPromociones").asBoolean()).isTrue();
        OffsetDateTime fecha = OffsetDateTime.parse(j.get("consentimientoPromocionesFecha").asText());
        assertThat(fecha).isBetween(OffsetDateTime.now().minusMinutes(2), OffsetDateTime.now().plusMinutes(1));
        assertThat(j.get("numeroHistoriaClinica").asText()).isEqualTo("HC-" + dni);
        assertThat(j.get("totalInasistencias").asInt()).isZero();
        assertThat(j.get("avisoInasistencias").asBoolean()).isFalse();
        assertThat(j.get("alergias")).hasSize(2);
        assertThat(j.get("alergias").get(0).asText()).isEqualTo("Polen");
        assertThat(j.get("alergias").get(1).asText()).isEqualTo("Latex");
        assertThat(j.get("antecedentesMedicos").asText()).isEqualTo("Hipertensión");
        assertThat(j.get("gestante").asBoolean()).isTrue();
        assertThat(j.get("semanasGestacion").asInt()).isEqualTo(12);
        assertThat(j.get("saldoPendiente").decimalValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(j.get("archivado").asBoolean()).isFalse();
        assertThat(j.get("tieneDeuda").asBoolean()).isFalse();
        assertThat(j.get("ultimaCitaAtendida").isNull()).isTrue();

        Map<String, Object> bd = pacienteBd(UUID.fromString(j.get("id").asText()));
        assertThat(bd.get("alergias")).isEqualTo("Polen,Latex");
        assertThat(bd.get("numero_historia_clinica")).as("'HC-'+dni es solo presentacion").isNull();
        assertThat(bd.get("tipo_documento")).isEqualTo("DNI");
        assertThat(bd.get("consentimiento_datos_en")).isNull();
        assertThat((BigDecimal) bd.get("saldo_pendiente")).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(bd.get("archivado")).isEqualTo(false);
        assertThat(bd.get("creado_en")).isNotNull();
    }

    @Test
    @DisplayName("Alta sin consentimiento de promociones: sin fecha; con numeroHistoriaClinica informado se devuelve tal cual")
    void altaSinConsentimientoYConHistoria() throws Exception {
        String sede = crearSede();
        Map<String, Object> body = cuerpoPaciente(sede);
        body.put("numeroHistoriaClinica", "HC-777");

        MvcResult r = crear(adminGeneral(), body);

        assertThat(r.getResponse().getStatus()).isEqualTo(201);
        JsonNode j = leer(r);
        assertThat(j.get("consentimientoPromociones").asBoolean()).isFalse();
        assertThat(j.get("consentimientoPromocionesFecha").isNull()).isTrue();
        assertThat(j.get("numeroHistoriaClinica").asText()).isEqualTo("HC-777");
        assertThat(j.get("alergias")).isEmpty();
        assertThat(j.get("gestante").asBoolean()).isFalse();
        assertThat(j.get("edad").asInt()).isZero();
        assertThat(j.get("nombreCompleto").asText()).isEqualTo(body.get("nombres") + " " + body.get("apellidos"));
        assertThat(pacienteBd(UUID.fromString(j.get("id").asText())).get("numero_historia_clinica")).isEqualTo("HC-777");
    }

    @Test
    @DisplayName("Alta: la validacion del DNI (8 digitos) se aplica ANTES del recorte, por lo que ' 12345678 ' se rechaza")
    void altaDniConEspaciosSeRechaza() throws Exception {
        String sede = crearSede();
        Map<String, Object> body = cuerpoPaciente(sede);
        String dni = (String) body.get("dni");
        body.put("dni", " " + dni + " ");

        MvcResult r = crear(adminGeneral(), body);

        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        assertThat(leer(r).get("detalles").get("dni").asText()).isEqualTo("El DNI debe tener exactamente 8 dígitos numéricos");
        assertThat(contar("SELECT count(*) FROM pacientes WHERE dni = ?", dni)).isZero();
    }

    @Test
    @DisplayName("Alta: validaciones del cuerpo devuelven 400 con 'detalles', 'message' y 'mensaje'; el email no se valida (B-051)")
    void altaValidaciones() throws Exception {
        String sede = crearSede();
        UsuarioPrueba admin = adminGeneral();
        String[][] casos = {
                {"dni", "1234567", "El DNI debe tener exactamente 8 dígitos numéricos"},
                {"dni", "123456789", "El DNI debe tener exactamente 8 dígitos numéricos"},
                {"dni", "ABCDEFGH", "El DNI debe tener exactamente 8 dígitos numéricos"},
                {"telefono", "812345678", "El celular debe tener 9 dígitos y empezar con 9"},
                {"telefono", "98765432", "El celular debe tener 9 dígitos y empezar con 9"},
                {"genero", "X", "El género debe ser MASCULINO, FEMENINO u OTRO"},
                {"fuenteCaptacion", "TWITTER", "Fuente de captación no válida"},
                {"fechaNacimiento", LocalDate.now().plusDays(1).toString(), "La fecha de nacimiento no puede ser futura"},
                {"nombres", "   ", "Los nombres son obligatorios"},
                {"apellidos", "", "Los apellidos son obligatorios"},
        };
        for (String[] caso : casos) {
            Map<String, Object> body = cuerpoPaciente(sede);
            body.put(caso[0], caso[1]);
            MvcResult r = crear(admin, body);
            assertThat(r.getResponse().getStatus()).as("%s=%s", caso[0], caso[1]).isEqualTo(400);
            JsonNode j = leer(r);
            assertThat(j.get("status").asInt()).isEqualTo(400);
            assertThat(j.get("error").asText()).isEqualTo("Validación fallida");
            assertThat(j.get("detalles").get(caso[0]).asText()).as("%s=%s", caso[0], caso[1]).isEqualTo(caso[2]);
            assertThat(j.get("message").asText()).isEqualTo(caso[2]);
            assertThat(j.get("mensaje").asText()).isEqualTo(caso[2]);
        }
        // sin dni ni nombres: varios errores, 'message' los une con '; '
        MvcResult r = crear(admin, new HashMap<>(Map.of("sedeId", sede)));
        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        assertThat(leer(r).get("detalles").size()).isEqualTo(3);
        // email arbitrario: no hay @Email
        Map<String, Object> conEmail = cuerpoPaciente(sede);
        conEmail.put("email", "no-es-un-correo");
        assertThat(crear(admin, conEmail).getResponse().getStatus()).isEqualTo(201);
        // cuerpo no JSON
        assertThat(ejecutar(post("/pacientes"), admin, "{no es json").getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("Alta con sede omitida: ADMIN_GENERAL 400 'Debe especificar una sede valida'; los demas roles toman su sede")
    void altaSedeOmitida() throws Exception {
        String sede = crearSede();
        MvcResult r = crear(adminGeneral(), cuerpoPaciente(null));
        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        assertThat(leer(r).get("message").asText()).isEqualTo("Debe especificar una sede válida para registrar al paciente.");

        for (String rol : List.of("ADMIN_SEDE", "ODONTOLOGO", "RECEPCION")) {
            MvcResult ok = crear(crearUsuario(rol, sede), cuerpoPaciente(null));
            assertThat(ok.getResponse().getStatus()).as(rol).isEqualTo(201);
            assertThat(leer(ok).get("sedeId").asText()).as(rol).isEqualTo(sede);
        }
        // sede en blanco se trata como omitida
        Map<String, Object> enBlanco = cuerpoPaciente("   ");
        assertThat(crear(adminGeneral(), enBlanco).getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("Alta en sede ajena: 403 para ADMIN_SEDE, ODONTOLOGO y RECEPCION, y no se crea el paciente")
    void altaSedeAjena() throws Exception {
        String propia = crearSede();
        String ajena = crearSede();
        for (String rol : List.of("ADMIN_SEDE", "ODONTOLOGO", "RECEPCION")) {
            UsuarioPrueba u = crearUsuario(rol, propia);
            Map<String, Object> body = cuerpoPaciente(ajena);
            MvcResult r = crear(u, body);
            assertThat(r.getResponse().getStatus()).as(rol).isEqualTo(403);
            JsonNode j = leer(r);
            assertThat(j.get("status").asInt()).isEqualTo(403);
            assertThat(j.get("error").asText()).isEqualTo("Forbidden");
            assertThat(j.get("message").asText()).isEqualTo("Acceso Denegado: Su usuario (" + u.email()
                    + ") está asignado a '" + propia + "' y no tiene permisos para acceder a la sede '" + ajena + "'.");
            assertThat(contar("SELECT count(*) FROM pacientes WHERE dni = ?", body.get("dni"))).isZero();
        }
        // un ADMIN_GENERAL si puede dar de alta en cualquier sede
        assertThat(crear(adminGeneral(), cuerpoPaciente(ajena)).getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    @DisplayName("Alta: sin token 401; DNI repetido (aun en otra sede) 409; sede inexistente 409 con el mismo mensaje generico")
    void altaAnonimoDuplicadoYSedeInexistente() throws Exception {
        String sede = crearSede();
        String otra = crearSede();
        assertThat(crear(null, cuerpoPaciente(sede)).getResponse().getStatus()).isEqualTo(401);

        UsuarioPrueba admin = adminGeneral();
        Map<String, Object> body = cuerpoPaciente(sede);
        assertThat(crear(admin, body).getResponse().getStatus()).isEqualTo(201);
        Map<String, Object> repetido = cuerpoPaciente(otra);
        repetido.put("dni", body.get("dni"));
        MvcResult dup = crear(admin, repetido);
        assertThat(dup.getResponse().getStatus()).isEqualTo(409);
        assertThat(leer(dup).get("message").asText()).isEqualTo("Ya existe un paciente registrado con ese documento.");
        assertThat(contar("SELECT count(*) FROM pacientes WHERE dni = ?", body.get("dni"))).isEqualTo(1);

        Map<String, Object> sinSede = cuerpoPaciente("sede-no-existe-" + tag());
        MvcResult fk = crear(admin, sinSede);
        assertThat(fk.getResponse().getStatus()).isEqualTo(409);
        assertThat(leer(fk).get("message").asText()).isEqualTo(MENSAJE_INTEGRIDAD);
        assertThat(contar("SELECT count(*) FROM pacientes WHERE dni = ?", sinSede.get("dni"))).isZero();
    }

    // ------------------------------------------------------------------ edicion

    @Test
    @DisplayName("Edicion: reemplaza los campos editables y conserva saldo, archivado, motivo, inasistencias, creado_en y numero de historia en blanco")
    void edicionReemplazaYConserva() throws Exception {
        String sede = crearSede();
        UsuarioPrueba admin = adminGeneral(); // nombre y DNI solo los cambia el dueño
        UUID id = insertarPaciente(sede, "Original", "Apellido");
        jdbc.update("UPDATE pacientes SET saldo_pendiente = 150, archivado = true, motivo_archivo = 'Se mudo', "
                        + "total_inasistencias = 3, numero_historia_clinica = 'HC-ORIG', telefono = '911111111', "
                        + "email = 'viejo@x.pe', alergias = 'Viejo', antecedentes_medicos = 'Viejo', es_gestante = true, "
                        + "semanas_gestacion = 8, creado_en = ? WHERE id = ?",
                Timestamp.valueOf("2020-01-02 03:04:05"), id);
        Object creadoAntes = pacienteBd(id).get("creado_en");
        String dniNuevo = dniUnico();

        Map<String, Object> body = new HashMap<>();
        body.put("dni", dniNuevo);
        body.put("nombres", "  Nuevo Nombre ");
        body.put("apellidos", " Nuevo Apellido  ");
        body.put("genero", "MASCULINO");
        body.put("fuenteCaptacion", "GOOGLE");
        body.put("alergias", List.of("Penicilina"));
        body.put("numeroHistoriaClinica", "   ");

        MvcResult r = editar(admin, id, body);

        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        JsonNode j = leer(r);
        assertThat(campos(j)).containsExactlyInAnyOrderElementsOf(CLAVES_PACIENTE);
        assertThat(j.get("id").asText()).isEqualTo(id.toString());
        assertThat(j.get("dni").asText()).isEqualTo(dniNuevo);
        assertThat(j.get("nombres").asText()).isEqualTo("Nuevo Nombre");
        assertThat(j.get("apellidos").asText()).isEqualTo("Nuevo Apellido");
        assertThat(j.get("sedeId").asText()).isEqualTo(sede);
        assertThat(j.get("telefono").isNull()).as("campos omitidos se ponen a null").isTrue();
        assertThat(j.get("email").isNull()).isTrue();
        assertThat(j.get("antecedentesMedicos").isNull()).isTrue();
        assertThat(j.get("gestante").asBoolean()).isFalse();
        assertThat(j.get("semanasGestacion").isNull()).isTrue();
        assertThat(j.get("saldoPendiente").decimalValue()).isEqualByComparingTo(new BigDecimal("150"));
        assertThat(j.get("tieneDeuda").asBoolean()).isTrue();
        assertThat(j.get("archivado").asBoolean()).isTrue();
        assertThat(j.get("totalInasistencias").asInt()).isEqualTo(3);
        assertThat(j.get("avisoInasistencias").asBoolean()).isTrue();
        assertThat(j.get("numeroHistoriaClinica").asText()).isEqualTo("HC-ORIG");

        Map<String, Object> bd = pacienteBd(id);
        assertThat((BigDecimal) bd.get("saldo_pendiente")).isEqualByComparingTo(new BigDecimal("150"));
        assertThat(bd.get("archivado")).isEqualTo(true);
        assertThat(bd.get("motivo_archivo")).isEqualTo("Se mudo");
        assertThat(bd.get("total_inasistencias")).isEqualTo(3);
        assertThat(bd.get("numero_historia_clinica")).isEqualTo("HC-ORIG");
        assertThat(bd.get("creado_en")).isEqualTo(creadoAntes);
        assertThat(bd.get("telefono")).isNull();
        assertThat(bd.get("email")).isNull();
        assertThat(bd.get("alergias")).isEqualTo("Penicilina");
        assertThat(bd.get("antecedentes_medicos")).isNull();
        assertThat(bd.get("es_gestante")).isEqualTo(false);

        // un numero de historia con texto SI sobrescribe
        Map<String, Object> conHistoria = new HashMap<>(body);
        conHistoria.put("dni", dniNuevo);
        conHistoria.put("numeroHistoriaClinica", "HC-NUEVA");
        assertThat(editar(admin, id, conHistoria).getResponse().getStatus()).isEqualTo(200);
        assertThat(pacienteBd(id).get("numero_historia_clinica")).isEqualTo("HC-NUEVA");
    }

    @Test
    @DisplayName("Edicion: la fecha de consentimiento de promociones solo se fija en el paso falso->verdadero y NO se borra al revocar")
    void edicionFechaConsentimientoPromociones() throws Exception {
        String sede = crearSede();
        UsuarioPrueba admin = crearUsuario("RECEPCION", sede);
        Map<String, Object> alta = cuerpoPaciente(sede);
        UUID id = UUID.fromString(leer(crear(admin, alta)).get("id").asText());
        assertThat(pacienteBd(id).get("consentimiento_promociones_fecha")).isNull();

        alta.put("consentimientoPromociones", true);
        assertThat(editar(admin, id, alta).getResponse().getStatus()).isEqualTo(200);
        Map<String, Object> tras1 = pacienteBd(id);
        assertThat(tras1.get("consentimiento_promociones")).isEqualTo(true);
        Object fecha1 = tras1.get("consentimiento_promociones_fecha");
        assertThat(fecha1).isNotNull();

        // verdadero -> verdadero: la fecha no cambia
        assertThat(editar(admin, id, alta).getResponse().getStatus()).isEqualTo(200);
        assertThat(pacienteBd(id).get("consentimiento_promociones_fecha")).isEqualTo(fecha1);

        // revocar: el flag pasa a false y la fecha se CONSERVA
        alta.put("consentimientoPromociones", false);
        MvcResult revocado = editar(admin, id, alta);
        assertThat(leer(revocado).get("consentimientoPromociones").asBoolean()).isFalse();
        assertThat(leer(revocado).get("consentimientoPromocionesFecha").isNull()).isFalse();
        Map<String, Object> tras3 = pacienteBd(id);
        assertThat(tras3.get("consentimiento_promociones")).isEqualTo(false);
        assertThat(tras3.get("consentimiento_promociones_fecha")).isEqualTo(fecha1);

        // volver a aceptar: nueva fecha
        Thread.sleep(20);
        alta.put("consentimientoPromociones", true);
        assertThat(editar(admin, id, alta).getResponse().getStatus()).isEqualTo(200);
        Timestamp fecha4 = (Timestamp) pacienteBd(id).get("consentimiento_promociones_fecha");
        assertThat(fecha4).isAfter((Timestamp) fecha1);
    }

    @Test
    @DisplayName("B-043: editar un paciente (PUT) conserva consentimiento_datos_en cuando ya hubiera un consentimiento de tratamiento de datos")
    void b043EditarConservaConsentimientoDatosEn() throws Exception {
        String sede = crearSede();
        UsuarioPrueba admin = adminGeneral(); // nombre y DNI solo los cambia el dueño
        Map<String, Object> alta = cuerpoPaciente(sede);
        UUID id = UUID.fromString(leer(crear(admin, alta)).get("id").asText());

        // 1) consentimiento real de tratamiento de datos por HTTP: fija la fecha
        Map<String, Object> consentimiento = new HashMap<>();
        consentimiento.put("pacienteId", id.toString());
        consentimiento.put("tipo", "TRATAMIENTO_DATOS");
        consentimiento.put("contenidoAceptado", "Autorizo el tratamiento de mis datos");
        consentimiento.put("firmaBase64", PNG_BASE64);
        consentimiento.put("nombreFirmante", "Firmante B043");
        consentimiento.put("documentoFirmante", "12345678");
        MvcResult c = ejecutar(post("/consentimientos"), admin, consentimiento);
        assertThat(c.getResponse().getStatus()).isEqualTo(201);
        assertThat(pacienteBd(id).get("consentimiento_datos_en")).as("tras firmar").isNotNull();

        // 2) PUT /pacientes: la fecha se conserva
        alta.put("nombres", "Editado");
        MvcResult r = editar(admin, id, alta);
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        assertThat(pacienteBd(id).get("consentimiento_datos_en")).as("tras editar").isNotNull();
        // el consentimiento firmado sigue existiendo (tabla inmutable)
        assertThat(contar("SELECT count(*) FROM consentimientos WHERE paciente_id = ?", id)).isEqualTo(1);

        // 3) igual con la fecha escrita por SQL
        jdbc.update("UPDATE pacientes SET consentimiento_datos_en = now() WHERE id = ?", id);
        assertThat(pacienteBd(id).get("consentimiento_datos_en")).isNotNull();
        assertThat(editar(admin, id, alta).getResponse().getStatus()).isEqualTo(200);
        assertThat(pacienteBd(id).get("consentimiento_datos_en")).isNotNull();
    }

    @Test
    @DisplayName("Edicion: id inexistente 400 'Paciente no encontrado' (B-045); sede del paciente ajena 403; cambio a sede ajena 403")
    void edicionErroresDeIdYSede() throws Exception {
        String sedeA = crearSede();
        String sedeB = crearSede();
        UUID enA = insertarPaciente(sedeA, "En", "SedeA");
        UsuarioPrueba adminA = crearUsuario("ADMIN_SEDE", sedeA);
        UsuarioPrueba adminB = crearUsuario("ADMIN_SEDE", sedeB);

        MvcResult inexistente = editar(adminA, UUID.randomUUID(), cuerpoPaciente(sedeA));
        assertThat(inexistente.getResponse().getStatus()).isEqualTo(400);
        assertThat(leer(inexistente).get("message").asText()).isEqualTo("Paciente no encontrado");

        // el 400 por inexistente precede a cualquier comprobacion de sede
        assertThat(editar(adminB, UUID.randomUUID(), cuerpoPaciente(sedeA)).getResponse().getStatus()).isEqualTo(400);

        // paciente de otra sede
        MvcResult ajeno = editar(adminB, enA, cuerpoPaciente(sedeB));
        assertThat(ajeno.getResponse().getStatus()).isEqualTo(403);
        assertThat(pacienteBd(enA).get("nombres")).isEqualTo("En");

        // cambio de sede a una ajena: 403 y nada cambia
        Map<String, Object> aOtraSede = cuerpoPaciente(sedeB);
        MvcResult cambio = editar(adminA, enA, aOtraSede);
        assertThat(cambio.getResponse().getStatus()).isEqualTo(403);
        assertThat(pacienteBd(enA).get("sede_id")).isEqualTo(sedeA);

        // sin token
        assertThat(editar(null, enA, cuerpoPaciente(sedeA)).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("Edicion: sede omitida conserva la del paciente; ADMIN_GENERAL puede mover de sede; sede inexistente 409; DNI repetido 409")
    void edicionSedeYDuplicados() throws Exception {
        String sedeA = crearSede();
        String sedeB = crearSede();
        UsuarioPrueba admin = adminGeneral();
        UUID id = insertarPaciente(sedeA, "Mover", "Paciente");
        UUID otro = insertarPaciente(sedeA, "Otro", "Paciente");
        String dniOtro = (String) pacienteBd(otro).get("dni");

        Map<String, Object> sinSede = cuerpoPaciente(null);
        MvcResult r1 = editar(admin, id, sinSede);
        assertThat(r1.getResponse().getStatus()).isEqualTo(200);
        assertThat(leer(r1).get("sedeId").asText()).isEqualTo(sedeA);

        Map<String, Object> aB = cuerpoPaciente(sedeB);
        MvcResult r2 = editar(admin, id, aB);
        assertThat(r2.getResponse().getStatus()).isEqualTo(200);
        assertThat(leer(r2).get("sedeId").asText()).isEqualTo(sedeB);
        assertThat(pacienteBd(id).get("sede_id")).isEqualTo(sedeB);

        Map<String, Object> inexistente = cuerpoPaciente("sede-no-existe-" + tag());
        MvcResult r3 = editar(admin, id, inexistente);
        assertThat(r3.getResponse().getStatus()).isEqualTo(409);
        assertThat(leer(r3).get("message").asText()).isEqualTo(MENSAJE_INTEGRIDAD);
        assertThat(pacienteBd(id).get("sede_id")).isEqualTo(sedeB);

        Map<String, Object> repetido = cuerpoPaciente(sedeB);
        repetido.put("dni", dniOtro);
        MvcResult r4 = editar(admin, id, repetido);
        assertThat(r4.getResponse().getStatus()).isEqualTo(409);
        assertThat(pacienteBd(id).get("dni")).isNotEqualTo(dniOtro);
    }

    @Test
    @DisplayName("Edicion por roles: ADMIN_GENERAL, ADMIN_SEDE, ODONTOLOGO y RECEPCION editan pacientes de su alcance; validacion 400")
    void edicionPorRoles() throws Exception {
        String sede = crearSede();
        UUID id = insertarPaciente(sede, "Rol", "Paciente");
        for (String rol : List.of("ADMIN_GENERAL", "ADMIN_SEDE", "ODONTOLOGO", "RECEPCION")) {
            UsuarioPrueba u = crearUsuario(rol, "ADMIN_GENERAL".equals(rol) ? null : sede);
            if ("ODONTOLOGO".equals(rol)) vincularCita(id, u);
            Map<String, Object> body = cuerpoPaciente("ADMIN_GENERAL".equals(rol) ? sede : null);
            body.put("nombres", "Editado " + rol);
            MvcResult r = editar(u, id, body);
            if ("ADMIN_GENERAL".equals(rol)) {
                assertThat(r.getResponse().getStatus()).as(rol).isEqualTo(200);
                assertThat(pacienteBd(id).get("nombres")).isEqualTo("Editado " + rol);
            } else {
                // nombre y DNI solo los cambia el dueño; los demás roles piden la corrección
                assertThat(r.getResponse().getStatus()).as(rol).isEqualTo(403);
                assertThat(pacienteBd(id).get("nombres")).isEqualTo("Editado ADMIN_GENERAL");
                Map<String, Object> soloContacto = new HashMap<>();
                soloContacto.put("dni", pacienteBd(id).get("dni"));
                soloContacto.put("nombres", pacienteBd(id).get("nombres"));
                soloContacto.put("apellidos", pacienteBd(id).get("apellidos"));
                soloContacto.put("telefono", "987654321");
                assertThat(editar(u, id, soloContacto).getResponse().getStatus()).as(rol + " contacto").isEqualTo(200);
            }
        }
        Map<String, Object> invalido = cuerpoPaciente(sede);
        invalido.put("telefono", "123");
        assertThat(editar(adminGeneral(), id, invalido).getResponse().getStatus()).isEqualTo(400);
    }

    // ------------------------------------------------------------------ GET por id

    @Test
    @DisplayName("GET /pacientes/{id}: 200 con las 29 claves; ultimaCitaAtendida siempre null aqui aunque haya cita ATENDIDA; ADMIN_GENERAL ve cualquier sede")
    void obtenerPorId() throws Exception {
        String sede = crearSede();
        UUID id = insertarPaciente(sede, "Consulta", "PorId");
        insertarCita(id, sede, "ATENDIDA", LocalDate.now().minusDays(3));
        jdbc.update("UPDATE pacientes SET alergias = 'Polen', saldo_pendiente = 20.50, fecha_nacimiento = ? WHERE id = ?",
                LocalDate.of(2000, 1, 31), id);
        String dni = (String) pacienteBd(id).get("dni");

        for (String rol : List.of("ADMIN_GENERAL", "ADMIN_SEDE", "ODONTOLOGO", "RECEPCION")) {
            UsuarioPrueba u = crearUsuario(rol, "ADMIN_GENERAL".equals(rol) ? null : sede);
            if ("ODONTOLOGO".equals(rol)) vincularCita(id, u);
            MvcResult r = ejecutar(get("/pacientes/" + id), u);
            assertThat(r.getResponse().getStatus()).as(rol).isEqualTo(200);
            JsonNode j = leer(r);
            assertThat(campos(j)).containsExactlyInAnyOrderElementsOf(CLAVES_PACIENTE);
            assertThat(j.get("id").asText()).isEqualTo(id.toString());
            assertThat(j.get("dni").asText()).isEqualTo(dni);
            assertThat(j.get("nombreCompleto").asText()).isEqualTo("Consulta PorId");
            assertThat(j.get("numeroHistoriaClinica").asText()).isEqualTo("HC-" + dni);
            assertThat(j.get("saldoPendiente").decimalValue()).isEqualByComparingTo(new BigDecimal("20.50"));
            assertThat(j.get("tieneDeuda").asBoolean()).isTrue();
            assertThat(j.get("edad").asInt()).isEqualTo(Period.between(LocalDate.of(2000, 1, 31), LocalDate.now()).getYears());
            assertThat(j.get("ultimaCitaAtendida").isNull()).isTrue();
        }
    }

    @Test
    @DisplayName("GET /pacientes/{id}: 400 'Paciente no encontrado' si no existe (B-045); 403 de otra sede; 401 sin token; 400 por UUID mal formado")
    void obtenerPorIdErrores() throws Exception {
        String sedeA = crearSede();
        String sedeB = crearSede();
        UUID enA = insertarPaciente(sedeA, "De", "SedeA");

        for (String rol : List.of("ADMIN_SEDE", "ODONTOLOGO", "RECEPCION")) {
            MvcResult r = ejecutar(get("/pacientes/" + enA), crearUsuario(rol, sedeB));
            assertThat(r.getResponse().getStatus()).as(rol).isEqualTo(403);
        }
        MvcResult inexistente = ejecutar(get("/pacientes/" + UUID.randomUUID()), adminGeneral());
        assertThat(inexistente.getResponse().getStatus()).isEqualTo(400);
        JsonNode j = leer(inexistente);
        assertThat(j.get("status").asInt()).isEqualTo(400);
        assertThat(j.get("error").asText()).isEqualTo("Bad Request");
        assertThat(j.get("message").asText()).isEqualTo("Paciente no encontrado");
        assertThat(j.get("mensaje").asText()).isEqualTo("Paciente no encontrado");
        assertThat(campos(j)).containsExactlyInAnyOrder("timestamp", "status", "error", "message", "mensaje");

        // un usuario de sede tambien recibe 400 (y no 403) si el id no existe
        assertThat(ejecutar(get("/pacientes/" + UUID.randomUUID()), crearUsuario("RECEPCION", sedeB))
                .getResponse().getStatus()).isEqualTo(400);

        assertThat(ejecutar(get("/pacientes/" + enA), null).getResponse().getStatus()).isEqualTo(401);
        MvcResult malo = ejecutar(get("/pacientes/no-es-uuid"), adminGeneral());
        assertThat(malo.getResponse().getStatus()).isEqualTo(400);
        assertThat(leer(malo).get("message").asText()).isEqualTo("El parámetro 'id' tiene un valor inválido.");
    }

    @Test
    @DisplayName("Un paciente archivado sigue accesible por id con archivado=true")
    void archivadoAccesiblePorId() throws Exception {
        String sede = crearSede();
        UUID id = insertarPaciente(sede, "Archivado", "Consultable");
        jdbc.update("UPDATE pacientes SET archivado = true, motivo_archivo = 'motivo' WHERE id = ?", id);
        MvcResult r = ejecutar(get("/pacientes/" + id), crearUsuario("RECEPCION", sede));
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        assertThat(leer(r).get("archivado").asBoolean()).isTrue();
    }
}
