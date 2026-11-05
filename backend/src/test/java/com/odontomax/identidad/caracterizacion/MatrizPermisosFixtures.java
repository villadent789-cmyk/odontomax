package com.odontomax.identidad.caracterizacion;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * Petición concreta (ruta real, parámetros y cuerpo VÁLIDO) para cada endpoint de la matriz.
 * Los cuerpos deben pasar @Valid: la validación de argumentos se ejecuta antes que @PreAuthorize y un 400
 * taparía el 403 (plan, 10.3 y riesgo R4). Todas las sedes usadas son la propia del sujeto.
 */
final class MatrizPermisosFixtures {

    static final String SEDE = "sede-chilca-1";
    static final UUID ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private static final Map<String, Supplier<MockHttpServletRequestBuilder>> PETICIONES = new HashMap<>();

    private MatrizPermisosFixtures() {
    }

    static MockHttpServletRequestBuilder peticion(String verbo, String ruta) {
        Supplier<MockHttpServletRequestBuilder> s = PETICIONES.get(verbo + " " + ruta);
        if (s == null) {
            throw new IllegalStateException("Falta fixture de petición para " + verbo + " " + ruta);
        }
        return s.get();
    }

    static boolean tieneFixture(String verbo, String ruta) {
        return PETICIONES.containsKey(verbo + " " + ruta);
    }

    private static void sinCuerpo(String verbo, String plantilla, String rutaReal) {
        PETICIONES.put(verbo + " " + plantilla, () -> request(HttpMethod.valueOf(verbo), rutaReal));
    }

    private static void conCuerpo(String verbo, String plantilla, String rutaReal, String json) {
        PETICIONES.put(verbo + " " + plantilla, () -> request(HttpMethod.valueOf(verbo), rutaReal)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    static {
        String id = ID.toString();
        String paciente = "{\"dni\":\"12345678\",\"nombres\":\"Ana\",\"apellidos\":\"Rojas\",\"sedeId\":\"" + SEDE + "\"}";
        String especialista = "{\"nombre\":\"Dra. Prueba\",\"especialidad\":\"Ortodoncia\",\"sedes\":[\"" + SEDE + "\"]}";
        String sede = "{\"nombre\":\"Sede Prueba\",\"direccion\":\"Av. Siempre Viva 123\",\"sillones\":2}";
        String servicio = "{\"nombre\":\"Limpieza\",\"especialidad\":\"General\",\"duracionMin\":30}";
        String pieza = "{\"piezaFdi\":11,\"estado\":\"CARIES\"}";

        // agenda
        sinCuerpo("GET", "/reservas-publicas/catalogo/especialistas", "/reservas-publicas/catalogo/especialistas");
        sinCuerpo("GET", "/citas", "/citas?sedeId=" + SEDE);
        sinCuerpo("GET", "/citas/reservas-web-pendientes", "/citas/reservas-web-pendientes?sedeId=" + SEDE);
        sinCuerpo("GET", "/citas/lista-espera", "/citas/lista-espera?sedeId=" + SEDE);
        conCuerpo("POST", "/citas", "/citas", "{\"pacienteId\":\"" + id + "\",\"fecha\":\"2026-12-01\",\"hora\":\"10:00\","
                + "\"sedeId\":\"" + SEDE + "\"}");
        conCuerpo("PATCH", "/citas/{id}/estado", "/citas/" + id + "/estado", "{\"estado\":\"CONFIRMADA\"}");
        sinCuerpo("POST", "/citas/{id}/iniciar-atencion", "/citas/" + id + "/iniciar-atencion");
        conCuerpo("PATCH", "/citas/{id}/reprogramar", "/citas/" + id + "/reprogramar",
                "{\"nuevaFecha\":\"2026-12-02\",\"nuevoHorario\":\"11:00\",\"motivo\":\"Paciente solicita cambio\"}");
        conCuerpo("PATCH", "/citas/{id}/cancelar", "/citas/" + id + "/cancelar", "{\"motivo\":\"No puede asistir\"}");
        sinCuerpo("GET", "/citas/bandeja-tareas", "/citas/bandeja-tareas?sedeId=" + SEDE);
        sinCuerpo("POST", "/citas/procesar-expiradas", "/citas/procesar-expiradas");
        conCuerpo("POST", "/citas/{id}/adelanto", "/citas/" + id + "/adelanto",
                "{\"monto\":30.00,\"medioPago\":\"YAPE\",\"numeroOperacion\":\"123456\"}");
        conCuerpo("POST", "/citas/{id}/contacto", "/citas/" + id + "/contacto",
                "{\"medio\":\"WHATSAPP\",\"resultado\":\"CONFIRMO\",\"notas\":\"Confirmado\"}");
        sinCuerpo("GET", "/especialistas", "/especialistas?sedeId=" + SEDE);
        sinCuerpo("GET", "/especialistas/admin", "/especialistas/admin");
        conCuerpo("POST", "/especialistas", "/especialistas", especialista);
        conCuerpo("PUT", "/especialistas/{id}", "/especialistas/" + id, especialista);
        conCuerpo("PATCH", "/especialistas/{id}/sillones", "/especialistas/" + id + "/sillones",
                "{\"sillonesPorSede\":{\"" + SEDE + "\":1},\"sedesSinSillon\":[]}");
        sinCuerpo("GET", "/especialistas/{id}/meta", "/especialistas/" + id + "/meta");
        conCuerpo("PATCH", "/especialistas/{id}/meta", "/especialistas/" + id + "/meta",
                "{\"metaMensual\":20000,\"comisionExcedentePorcentaje\":10}");
        sinCuerpo("GET", "/reportes/rendimiento-doctores", "/reportes/rendimiento-doctores?mes=2026-10&sedeId=" + SEDE);
        sinCuerpo("GET", "/rendimiento/mi-avance", "/rendimiento/mi-avance?mes=2026-10");
        sinCuerpo("PATCH", "/especialistas/{id}/estado", "/especialistas/" + id + "/estado?activo=true");
        sinCuerpo("DELETE", "/especialistas/{id}", "/especialistas/" + id);
        sinCuerpo("GET", "/especialistas/{id}/horarios", "/especialistas/" + id + "/horarios");
        conCuerpo("PUT", "/especialistas/{id}/horarios", "/especialistas/" + id + "/horarios", "[]");
        sinCuerpo("GET", "/especialistas/{id}/disponibilidad",
                "/especialistas/" + id + "/disponibilidad?fecha=2026-12-01&sedeId=" + SEDE);
        conCuerpo("POST", "/reservas-publicas", "/reservas-publicas", "{\"dni\":\"12345678\",\"nombres\":\"Ana\","
                + "\"apellidos\":\"Rojas\",\"telefono\":\"987654321\",\"motivoConsulta\":\"Dolor\",\"sedeId\":\"" + SEDE
                + "\",\"fechaPreferencia\":\"2026-12-01\",\"turno\":\"MANANA\"}");
        conCuerpo("POST", "/reservas-publicas/consulta", "/reservas-publicas/consulta",
                "{\"ticket\":\"OM-TEST1234\",\"dni\":\"12345678\"}");

        // pacientes
        sinCuerpo("GET", "/antecedentes/pacientes/{pacienteId}/historial", "/antecedentes/pacientes/" + id + "/historial");
        conCuerpo("PUT", "/antecedentes/pacientes/{pacienteId}", "/antecedentes/pacientes/" + id,
                "{\"motivoCambio\":\"Actualización de alergias\"}");
        sinCuerpo("GET", "/consentimientos/plantillas", "/consentimientos/plantillas");
        sinCuerpo("GET", "/consentimientos/pacientes/{pacienteId}", "/consentimientos/pacientes/" + id);
        conCuerpo("POST", "/consentimientos", "/consentimientos", "{\"pacienteId\":\"" + id + "\",\"tipo\":\"TRATAMIENTO_DATOS\","
                + "\"contenidoAceptado\":\"Acepto\",\"firmaBase64\":\"data:image/png;base64,AAAA\","
                + "\"nombreFirmante\":\"Ana Rojas\",\"documentoFirmante\":\"12345678\"}");
        sinCuerpo("POST", "/historia-clinica/pacientes/{pacienteId}/apertura", "/historia-clinica/pacientes/" + id + "/apertura");
        sinCuerpo("GET", "/historia-clinica/accesos", "/historia-clinica/accesos");
        sinCuerpo("GET", "/pacientes/{id}/exportar", "/pacientes/" + id + "/exportar");
        sinCuerpo("GET", "/pacientes", "/pacientes?sedeId=" + SEDE);
        sinCuerpo("GET", "/pacientes/{id}", "/pacientes/" + id);
        sinCuerpo("GET", "/pacientes/cumpleanos-manana", "/pacientes/cumpleanos-manana");
        sinCuerpo("GET", "/consentimientos/{id}/firma", "/consentimientos/" + id + "/firma");
        sinCuerpo("GET", "/consentimientos/{id}/pdf", "/consentimientos/" + id + "/pdf");
        sinCuerpo("GET", "/radiografias/{id}/archivo", "/radiografias/" + id + "/archivo");
        conCuerpo("POST", "/pacientes", "/pacientes", paciente);
        conCuerpo("PUT", "/pacientes/{id}", "/pacientes/" + id, paciente);
        conCuerpo("POST", "/pacientes/{id}/archivar", "/pacientes/" + id + "/archivar", "{\"motivo\":\"Traslado\"}");
        sinCuerpo("DELETE", "/pacientes/{id}", "/pacientes/" + id + "?confirmacion=true");

        // identidad y auditoría
        sinCuerpo("GET", "/auditoria", "/auditoria");
        conCuerpo("POST", "/auth/login", "/auth/login", "{\"email\":\"alguien@odontomax.pe\",\"password\":\"Clave-1\"}");
        conCuerpo("POST", "/auth/2fa/login", "/auth/2fa/login", "{\"temp_token\":\"x.y.z\",\"codigo\":\"123456\"}");
        sinCuerpo("GET", "/auth/me", "/auth/me");
        conCuerpo("POST", "/auth/cambiar-password", "/auth/cambiar-password",
                "{\"password_actual\":\"Actual-1\",\"password_nuevo\":\"Nueva-12\"}");
        sinCuerpo("POST", "/auth/2fa/configurar", "/auth/2fa/configurar");
        conCuerpo("POST", "/auth/2fa/verificar-activar", "/auth/2fa/verificar-activar", "{\"codigo\":123456}");
        conCuerpo("POST", "/auth/2fa/desactivar", "/auth/2fa/desactivar",
                "{\"password_actual\":\"Actual-1\",\"codigo\":\"123456\"}");
        sinCuerpo("GET", "/usuarios", "/usuarios");
        conCuerpo("POST", "/usuarios", "/usuarios", "{\"nombre\":\"Nuevo Usuario\",\"email\":\"nuevo@odontomax.pe\","
                + "\"password\":\"Clave-12\",\"rol\":\"RECEPCION\",\"sedeId\":\"" + SEDE + "\"}");
        conCuerpo("PUT", "/usuarios/{id}", "/usuarios/" + id,
                "{\"nombre\":\"Usuario Editado\",\"rol\":\"RECEPCION\",\"sedeId\":\"" + SEDE + "\"}");
        sinCuerpo("PATCH", "/usuarios/{id}/estado", "/usuarios/" + id + "/estado?activo=true");
        conCuerpo("POST", "/usuarios/{id}/reset-password", "/usuarios/" + id + "/reset-password",
                "{\"newPassword\":\"Nueva-12\"}");
        sinCuerpo("DELETE", "/usuarios/{id}", "/usuarios/" + id);
        sinCuerpo("POST", "/usuarios/{id}/2fa/restablecer", "/usuarios/" + id + "/2fa/restablecer");

        // finanzas
        sinCuerpo("GET", "/caja/activa", "/caja/activa?sedeId=" + SEDE);
        sinCuerpo("GET", "/caja/historial", "/caja/historial?sedeId=" + SEDE);
        sinCuerpo("GET", "/caja/resumen-diario", "/caja/resumen-diario?sedeId=" + SEDE + "&fecha=2026-12-01");
        sinCuerpo("GET", "/caja/{id}", "/caja/" + id);
        conCuerpo("POST", "/caja/abrir", "/caja/abrir", "{\"sedeId\":\"" + SEDE + "\",\"montoApertura\":0}");
        conCuerpo("POST", "/caja/{id}/movimientos", "/caja/" + id + "/movimientos",
                "{\"tipo\":\"INGRESO\",\"concepto\":\"Venta\",\"monto\":10}");
        conCuerpo("POST", "/caja/{id}/cerrar", "/caja/" + id + "/cerrar", "{\"montoContado\":0}");
        sinCuerpo("GET", "/gastos", "/gastos?sedeId=" + SEDE);
        sinCuerpo("GET", "/gastos/{id}", "/gastos/" + id);
        conCuerpo("POST", "/gastos", "/gastos", "{\"sedeId\":\"" + SEDE + "\",\"categoria\":\"INSUMOS\","
                + "\"concepto\":\"Guantes\",\"monto\":10}");
        conCuerpo("POST", "/gastos/{id}/anular", "/gastos/" + id + "/anular", "{\"motivoAnulacion\":\"Error\"}");
        sinCuerpo("GET", "/pagos/paciente/{pacienteId}", "/pagos/paciente/" + id);
        sinCuerpo("GET", "/pagos/sede/{sedeId}", "/pagos/sede/" + SEDE);
        sinCuerpo("GET", "/pagos/{id}", "/pagos/" + id);
        conCuerpo("POST", "/pagos", "/pagos", "{\"pacienteId\":\"" + id + "\",\"sedeId\":\"" + SEDE
                + "\",\"monto\":10,\"metodoPago\":\"EFECTIVO\"}");
        conCuerpo("POST", "/pagos/{id}/anular", "/pagos/" + id + "/anular", "{\"motivoAnulacion\":\"Error\"}");

        // inventario
        sinCuerpo("GET", "/inventario/productos", "/inventario/productos?sedeId=" + SEDE);
        sinCuerpo("GET", "/inventario/productos/{id}", "/inventario/productos/" + id);
        conCuerpo("POST", "/inventario/productos", "/inventario/productos", "{\"nombre\":\"Guantes\",\"stock_minimo\":5}");
        conCuerpo("PUT", "/inventario/productos/{id}", "/inventario/productos/" + id, "{\"nombre\":\"Guantes de Nitrilo\",\"stock_minimo\":5}");
        sinCuerpo("DELETE", "/inventario/productos/{id}", "/inventario/productos/" + id);
        conCuerpo("POST", "/inventario/movimientos", "/inventario/movimientos", "{\"producto_id\":\"" + id
                + "\",\"sede_id\":\"" + SEDE + "\",\"tipo\":\"ENTRADA\",\"cantidad\":1}");
        sinCuerpo("GET", "/inventario/movimientos", "/inventario/movimientos?sedeId=" + SEDE);
        sinCuerpo("GET", "/inventario/transferencias", "/inventario/transferencias");
        conCuerpo("POST", "/inventario/transferencias", "/inventario/transferencias", "{\"producto_id\":\"" + id
                + "\",\"cantidad\":1,\"sede_origen_id\":\"" + SEDE + "\",\"sede_destino_id\":\"sede-villamax-chilca\"}");
        sinCuerpo("PATCH", "/inventario/transferencias/{id}/recibir", "/inventario/transferencias/" + id + "/recibir");
        sinCuerpo("PATCH", "/inventario/transferencias/{id}/cancelar", "/inventario/transferencias/" + id + "/cancelar");

        // clínica
        sinCuerpo("GET", "/evoluciones/pacientes/{pacienteId}", "/evoluciones/pacientes/" + id);
        conCuerpo("POST", "/evoluciones/pacientes/{pacienteId}", "/evoluciones/pacientes/" + id,
                "{\"contenido\":\"Control sin novedad\"}");
        sinCuerpo("GET", "/odontograma/pacientes/{pacienteId}", "/odontograma/pacientes/" + id);
        sinCuerpo("GET", "/odontograma/pacientes/{pacienteId}/historial", "/odontograma/pacientes/" + id + "/historial");
        conCuerpo("POST", "/odontograma/pacientes/{pacienteId}/pieza", "/odontograma/pacientes/" + id + "/pieza", pieza);
        conCuerpo("POST", "/odontograma/pacientes/{pacienteId}/lote", "/odontograma/pacientes/" + id + "/lote",
                "{\"piezas\":[" + pieza + "]}");
        sinCuerpo("GET", "/odontograma/pacientes/{pacienteId}/pdf", "/odontograma/pacientes/" + id + "/pdf");
        sinCuerpo("GET", "/radiografias/paciente/{pacienteId}", "/radiografias/paciente/" + id);
        sinCuerpo("GET", "/radiografias/{id}/url-firmada", "/radiografias/" + id + "/url-firmada");
        PETICIONES.put("POST /radiografias/subir", () -> multipart("/radiografias/subir")
                .file(new MockMultipartFile("archivo", "rx.png", "image/png", new byte[]{1, 2, 3}))
                .param("pacienteId", id).param("tipoEstudio", "PERIAPICAL"));
        sinCuerpo("DELETE", "/radiografias/{id}", "/radiografias/" + id + "?motivo=Duplicada");
        conCuerpo("POST", "/recetas/pdf", "/recetas/pdf", "{\"pacienteNombre\":\"Ana Rojas\",\"doctorNombre\":\"Dr. X\","
                + "\"medicamentos\":[{\"medicamento\":\"Amoxicilina 500mg\"}]}");

        // tratamientos
        sinCuerpo("GET", "/tratamientos/paciente/{pacienteId}", "/tratamientos/paciente/" + id);
        sinCuerpo("GET", "/tratamientos/sede/{sedeId}", "/tratamientos/sede/" + SEDE);
        sinCuerpo("GET", "/tratamientos/{id}", "/tratamientos/" + id);
        conCuerpo("POST", "/tratamientos", "/tratamientos", "{\"pacienteId\":\"" + id + "\",\"sedeId\":\"" + SEDE
                + "\",\"especialistaId\":\"" + id + "\",\"nombre\":\"Ortodoncia\",\"costoTotal\":100}");
        conCuerpo("PATCH", "/tratamientos/{id}", "/tratamientos/" + id, "{\"estado\":\"EN_PROCESO\"}");
        conCuerpo("POST", "/tratamientos/{id}/aceptar", "/tratamientos/" + id + "/aceptar", "{\"aceptado\":true}");
        sinCuerpo("DELETE", "/tratamientos/{id}", "/tratamientos/" + id);

        // reportes, mantenimiento, sedes y servicios
        sinCuerpo("GET", "/reportes/financiero", "/reportes/financiero?sedeId=" + SEDE);
        sinCuerpo("GET", "/reportes/deudores", "/reportes/deudores?sedeId=" + SEDE);
        sinCuerpo("GET", "/reportes/citas-rendimiento", "/reportes/citas-rendimiento?sedeId=" + SEDE);
        sinCuerpo("GET", "/reportes/citas", "/reportes/citas?sedeId=" + SEDE);
        sinCuerpo("GET", "/reportes/pacientes", "/reportes/pacientes?sedeId=" + SEDE);
        sinCuerpo("GET", "/reportes/doctores", "/reportes/doctores?sedeId=" + SEDE);
        sinCuerpo("GET", "/reportes/inventario", "/reportes/inventario?sedeId=" + SEDE);
        sinCuerpo("GET", "/admin/mantenimiento/tamano-bd", "/admin/mantenimiento/tamano-bd");
        sinCuerpo("GET", "/reservas-publicas/catalogo/servicios", "/reservas-publicas/catalogo/servicios");
        sinCuerpo("GET", "/sedes", "/sedes");
        sinCuerpo("GET", "/sedes/admin", "/sedes/admin");
        conCuerpo("POST", "/sedes", "/sedes", sede);
        conCuerpo("PUT", "/sedes/{id}", "/sedes/" + SEDE, sede);
        sinCuerpo("PATCH", "/sedes/{id}/estado", "/sedes/" + SEDE + "/estado?activa=true");
        sinCuerpo("GET", "/servicios", "/servicios?sedeId=" + SEDE);
        sinCuerpo("GET", "/servicios/admin", "/servicios/admin");
        conCuerpo("POST", "/servicios", "/servicios", servicio);
        conCuerpo("PUT", "/servicios/{id}", "/servicios/srv-limpieza", servicio);
        sinCuerpo("PATCH", "/servicios/{id}/estado", "/servicios/srv-limpieza/estado?activo=true");

        // laboratorio dental
        String labOrdenCrear = "{\"pacienteId\":\"" + id + "\",\"especialistaId\":\"" + id + "\",\"sedeId\":\"" + SEDE
                + "\",\"laboratorioId\":\"" + id + "\",\"tipoTrabajo\":\"Corona Zirconio\"}";
        String labOrdenEditar = "{\"laboratorioId\":\"" + id + "\",\"tipoTrabajo\":\"Corona Zirconio\"}";
        String labProv = "{\"nombre\":\"Laboratorio Dental Test\"}";

        sinCuerpo("GET", "/laboratorio/ordenes", "/laboratorio/ordenes");
        sinCuerpo("GET", "/laboratorio/ordenes/{id}", "/laboratorio/ordenes/" + id);
        conCuerpo("POST", "/laboratorio/ordenes", "/laboratorio/ordenes", labOrdenCrear);
        conCuerpo("PUT", "/laboratorio/ordenes/{id}", "/laboratorio/ordenes/" + id, labOrdenEditar);
        conCuerpo("PATCH", "/laboratorio/ordenes/{id}/estado", "/laboratorio/ordenes/" + id + "/estado", "{\"nuevoEstado\":\"EN_PRUEBA\"}");
        conCuerpo("POST", "/laboratorio/ordenes/{id}/pruebas", "/laboratorio/ordenes/" + id + "/pruebas", "{\"resultado\":\"CORRECTO\"}");
        sinCuerpo("GET", "/odontograma/pacientes/{pacienteId}/episodios", "/odontograma/pacientes/" + id + "/episodios");
        conCuerpo("POST", "/odontograma/pacientes/{pacienteId}/episodios", "/odontograma/pacientes/" + id + "/episodios", "{}");
        conCuerpo("POST", "/odontograma/pacientes/{pacienteId}/episodios/{episodioId}/cerrar", "/odontograma/pacientes/" + id + "/episodios/" + id + "/cerrar", "{}");
        sinCuerpo("GET", "/presupuestos/paciente/{pacienteId}", "/presupuestos/paciente/" + id);
        sinCuerpo("GET", "/presupuestos/pendientes", "/presupuestos/pendientes?sedeId=" + SEDE);
        sinCuerpo("GET", "/plantillas-plan", "/plantillas-plan");
        sinCuerpo("GET", "/tratamientos/pacientes-sin-plan", "/tratamientos/pacientes-sin-plan?sedeId=" + SEDE);
        conCuerpo("POST", "/plantillas-plan", "/plantillas-plan", "{\"nombre\":\"Curación molar\",\"procedimientoId\":\"srv-1\",\"servicioNombre\":\"Curación\",\"piezas\":1,\"sesiones\":1}");
        sinCuerpo("DELETE", "/plantillas-plan/{id}", "/plantillas-plan/" + id);
        conCuerpo("PATCH", "/especialistas/mi-especialidad", "/especialistas/mi-especialidad", "{\"especialidad\":\"Ortodoncia\"}");
        conCuerpo("POST", "/pacientes/{id}/solicitudes-correccion", "/pacientes/" + id + "/solicitudes-correccion", "{\"nombres\":\"Nombre Corregido\",\"motivo\":\"Error al escribir\"}");
        sinCuerpo("GET", "/pacientes/{id}/solicitudes-correccion", "/pacientes/" + id + "/solicitudes-correccion");
        sinCuerpo("GET", "/pacientes/solicitudes-correccion/pendientes", "/pacientes/solicitudes-correccion/pendientes");
        conCuerpo("POST", "/pacientes/solicitudes-correccion/{solicitudId}/aprobar", "/pacientes/solicitudes-correccion/" + id + "/aprobar", "{\"comentario\":\"Autorizado\"}");
        conCuerpo("POST", "/pacientes/solicitudes-correccion/{solicitudId}/rechazar", "/pacientes/solicitudes-correccion/" + id + "/rechazar", "{\"comentario\":\"No procede\"}");
        conCuerpo("POST", "/presupuestos", "/presupuestos", "{\"pacienteId\":\"" + id + "\",\"sedeId\":\"" + SEDE
                + "\",\"items\":[{\"nombre\":\"Curación\",\"cantidad\":2,\"precioUnitario\":50}]}");
        conCuerpo("POST", "/presupuestos/{id}/aceptar", "/presupuestos/" + id + "/aceptar", "{}");
        sinCuerpo("POST", "/presupuestos/{id}/rechazar", "/presupuestos/" + id + "/rechazar");
        sinCuerpo("GET", "/pacientes/{pacienteId}/descuentos-recomendacion", "/pacientes/" + id + "/descuentos-recomendacion");
        conCuerpo("POST", "/pacientes/{pacienteId}/descuentos-recomendacion/{descuentoId}/aplicar", "/pacientes/" + id + "/descuentos-recomendacion/" + id + "/aplicar", "{}");
        sinCuerpo("GET", "/pacientes/{pacienteId}/ficha-ingreso", "/pacientes/" + id + "/ficha-ingreso");
        conCuerpo("PUT", "/pacientes/{pacienteId}/ficha-ingreso", "/pacientes/" + id + "/ficha-ingreso", "{}");
        sinCuerpo("GET", "/pacientes/{pacienteId}/foto", "/pacientes/" + id + "/foto");
        sinCuerpo("DELETE", "/pacientes/{pacienteId}/foto", "/pacientes/" + id + "/foto");
        PETICIONES.put("PUT /pacientes/{pacienteId}/foto", () -> multipart(org.springframework.http.HttpMethod.PUT, "/pacientes/" + id + "/foto")
                .file(new MockMultipartFile("archivo", "foto.jpg", "image/jpeg", new byte[]{1, 2, 3})));
        sinCuerpo("GET", "/laboratorio/proveedores", "/laboratorio/proveedores");
        conCuerpo("POST", "/laboratorio/proveedores", "/laboratorio/proveedores", labProv);
        conCuerpo("PUT", "/laboratorio/proveedores/{id}", "/laboratorio/proveedores/" + id, labProv);
        conCuerpo("POST", "/laboratorio/ordenes/{id}/pagos", "/laboratorio/ordenes/" + id + "/pagos",
                "{\"monto\":25.00,\"metodoPago\":\"YAPE\",\"numeroOperacion\":\"OP12345\"}");
        sinCuerpo("GET", "/laboratorio/ordenes/{id}/pagos", "/laboratorio/ordenes/" + id + "/pagos");
        conCuerpo("POST", "/laboratorio/pagos/{id}/anular", "/laboratorio/pagos/" + id + "/anular",
                "{\"motivoAnulacion\":\"Error de registro\"}");
        sinCuerpo("GET", "/laboratorio/cuentas", "/laboratorio/cuentas?sedeId=" + SEDE);

        // radiología - comisiones y derivadores
        String docDerivador = "{\"nombre\":\"Dr. Test Derivador\",\"porcentajeOTarifaFija\":10.00}";
        String comisionCrear = "{\"doctorDerivadorId\":\"" + id + "\",\"pacienteNombre\":\"Paciente Test\",\"tipoEstudio\":\"PANORAMICA\",\"precioCobrado\":50.00,\"montoComision\":10.00}";
        String comisionLiquidar = "{\"metodoPago\":\"YAPE\",\"codigoOperacion\":\"OP12345\"}";

        sinCuerpo("GET", "/inventario/servicios/{servicioId}/insumos", "/inventario/servicios/vd-limpieza/insumos");
        conCuerpo("PUT", "/inventario/servicios/{servicioId}/insumos", "/inventario/servicios/vd-limpieza/insumos", "[]");
        sinCuerpo("GET", "/inventario/alertas-consumo", "/inventario/alertas-consumo?sedeId=sede-chilca-1");
        sinCuerpo("POST", "/inventario/alertas-consumo/{id}/resolver", "/inventario/alertas-consumo/" + id + "/resolver");
        sinCuerpo("GET", "/plantillas", "/plantillas");
        sinCuerpo("GET", "/plantillas/servicio/{idONombre}", "/plantillas/servicio/tar-endo-mono");
        sinCuerpo("GET", "/plantillas/registros/paciente/{pacienteId}", "/plantillas/registros/paciente/" + id);
        conCuerpo("POST", "/plantillas/registros", "/plantillas/registros", "{\"pacienteId\":\"" + id + "\",\"plantillaCodigo\":\"CONSULTA\",\"datos\":{}}");
        conCuerpo("PUT", "/plantillas/registros/{id}", "/plantillas/registros/" + id, "{\"datos\":{}}");
        sinCuerpo("POST", "/plantillas/registros/{id}/cerrar", "/plantillas/registros/" + id + "/cerrar");
        sinCuerpo("GET", "/caja/reporte-diario", "/caja/reporte-diario?sedeId=sede-chilca-1");
        conCuerpo("POST", "/radiologia/comisiones/liquidar-lote", "/radiologia/comisiones/liquidar-lote",
                "{\"comisionIds\":[\"" + id + "\"],\"metodoPago\":\"EFECTIVO\"}");
        sinCuerpo("GET", "/radiologia/comisiones/estado-cuenta", "/radiologia/comisiones/estado-cuenta?doctorId=" + id + "&desde=2026-10-01&hasta=2026-10-31");
        sinCuerpo("GET", "/radiologia/comisiones/fallidas", "/radiologia/comisiones/fallidas");
        sinCuerpo("POST", "/citas/{id}/reintentar-comision", "/citas/" + id + "/reintentar-comision");
        conCuerpo("POST", "/citas/espera-hueco", "/citas/espera-hueco",
                "{\"sedeId\":\"sede-chilca-1\",\"pacienteId\":\"" + id + "\",\"motivo\":\"Limpieza\"}");
        sinCuerpo("GET", "/citas/espera-hueco", "/citas/espera-hueco?sedeId=sede-chilca-1");
        conCuerpo("PATCH", "/citas/espera-hueco/{id}/estado", "/citas/espera-hueco/" + id + "/estado", "{\"estado\":\"OFRECIDA\"}");
        sinCuerpo("GET", "/radiologia/derivadores", "/radiologia/derivadores");
        sinCuerpo("GET", "/radiologia/derivadores/{id}", "/radiologia/derivadores/" + id);
        conCuerpo("POST", "/radiologia/derivadores", "/radiologia/derivadores", docDerivador);
        conCuerpo("PUT", "/radiologia/derivadores/{id}", "/radiologia/derivadores/" + id, docDerivador);
        sinCuerpo("GET", "/radiologia/comisiones", "/radiologia/comisiones");
        sinCuerpo("GET", "/radiologia/comisiones/resumen", "/radiologia/comisiones/resumen");
        sinCuerpo("GET", "/radiologia/comisiones/{id}", "/radiologia/comisiones/" + id);
        conCuerpo("POST", "/radiologia/comisiones", "/radiologia/comisiones", comisionCrear);
        conCuerpo("PATCH", "/radiologia/comisiones/{id}/liquidar", "/radiologia/comisiones/" + id + "/liquidar", comisionLiquidar);
    }
}
