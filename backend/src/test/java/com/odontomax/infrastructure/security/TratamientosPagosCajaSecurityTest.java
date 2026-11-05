package com.odontomax.infrastructure.security;

import com.odontomax.identidad.testsupport.IdentidadSeguridadWebMvcConfig;
import com.odontomax.identidad.infrastructure.security.SecurityConfig;
import com.odontomax.identidad.infrastructure.security.JwtAuthenticationFilter;
import com.odontomax.identidad.infrastructure.security.JwtAuthenticationEntryPoint;
import com.odontomax.identidad.infrastructure.security.CustomAccessDeniedHandler;
import com.odontomax.identidad.infrastructure.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.odontomax.application.dto.request.*;
import com.odontomax.application.dto.response.*;
import com.odontomax.application.service.*;
import com.odontomax.identidad.infrastructure.persistence.entity.UsuarioEntity;
import com.odontomax.identidad.infrastructure.persistence.repository.SpringDataUsuarioRepository;
import com.odontomax.presentation.controller.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.odontomax.reportes.dto.ReporteCitasRendimientoResponseDTO;
import com.odontomax.reportes.dto.ReporteDeudoresResponseDTO;
import com.odontomax.reportes.dto.ReporteFinancieroResponseDTO;
import com.odontomax.reportes.service.ReporteService;
import com.odontomax.reportes.infrastructure.web.ReporteController;
import com.odontomax.tratamientos.service.TratamientoService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        TratamientoController.class,
        PagoController.class,
        CajaController.class,
        GastoController.class,
        ReporteController.class
})
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class, IdentidadSeguridadWebMvcConfig.class,
        JwtTokenProvider.class,
        JwtAuthenticationEntryPoint.class,
        CustomAccessDeniedHandler.class
})
@org.springframework.test.context.ContextConfiguration(initializers = TestSecurityEnvironmentInitializer.class)
class TratamientosPagosCajaSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TratamientoService tratamientoService;

    @MockBean
    private PagoService pagoService;

    @MockBean
    private CajaService cajaService;

    @MockBean
    private GastoService gastoService;

    @MockBean
    private ReporteService reporteService;

    @MockBean
    private com.odontomax.reportes.service.RendimientoDoctoresService rendimientoDoctoresService;

    @MockBean
    private SpringDataUsuarioRepository usuarioRepository;

    private UUID recepcionId;
    private UUID odontologoId;
    private UUID adminSedeId;
    private UUID adminGeneralId;

    private String tokenRecepcion;
    private String tokenOdontologo;
    private String tokenAdminSede;
    private String tokenAdminGeneral;

    @BeforeEach
    void setUp() {
        recepcionId = UUID.randomUUID();
        odontologoId = UUID.randomUUID();
        adminSedeId = UUID.randomUUID();
        adminGeneralId = UUID.randomUUID();

        // 1. Recepción (sede-chilca-1)
        when(usuarioRepository.findByEmailIgnoreCase("recep@odontomax.pe")).thenReturn(Optional.of(
                UsuarioEntity.builder()
                        .id(recepcionId)
                        .email("recep@odontomax.pe")
                        .nombre("Recepción Chilca")
                        .rol("RECEPCION")
                        .sedeId("sede-chilca-1")
                        .activo(true)
                        .build()
        ));
        tokenRecepcion = jwtTokenProvider.generateToken(
                recepcionId, "recep@odontomax.pe", "Recepción Chilca", "RECEPCION", "sede-chilca-1", 0);

        // 2. Odontólogo (sede-chilca-1)
        when(usuarioRepository.findByEmailIgnoreCase("doc@odontomax.pe")).thenReturn(Optional.of(
                UsuarioEntity.builder()
                        .id(odontologoId)
                        .email("doc@odontomax.pe")
                        .nombre("Dr. Odontólogo")
                        .rol("ODONTOLOGO")
                        .sedeId("sede-chilca-1")
                        .activo(true)
                        .build()
        ));
        tokenOdontologo = jwtTokenProvider.generateToken(
                odontologoId, "doc@odontomax.pe", "Dr. Odontólogo", "ODONTOLOGO", "sede-chilca-1", 0);

        // 3. Admin Sede (sede-chilca-1)
        when(usuarioRepository.findByEmailIgnoreCase("admin.chilca@odontomax.pe")).thenReturn(Optional.of(
                UsuarioEntity.builder()
                        .id(adminSedeId)
                        .email("admin.chilca@odontomax.pe")
                        .nombre("Admin Chilca")
                        .rol("ADMIN_SEDE")
                        .sedeId("sede-chilca-1")
                        .activo(true)
                        .build()
        ));
        tokenAdminSede = jwtTokenProvider.generateToken(
                adminSedeId, "admin.chilca@odontomax.pe", "Admin Chilca", "ADMIN_SEDE", "sede-chilca-1", 0);

        // 4. Admin General
        when(usuarioRepository.findByEmailIgnoreCase("admin@odontomax.pe")).thenReturn(Optional.of(
                UsuarioEntity.builder()
                        .id(adminGeneralId)
                        .email("admin@odontomax.pe")
                        .nombre("Admin General")
                        .rol("ADMIN_GENERAL")
                        .sedeId(null)
                        .activo(true)
                        .build()
        ));
        tokenAdminGeneral = jwtTokenProvider.generateToken(
                adminGeneralId, "admin@odontomax.pe", "Admin General", "ADMIN_GENERAL", null, 0);
    }

    // =========================================================================
    // 1. TRATAMIENTOS Y PRESUPUESTOS (2.1)
    // =========================================================================

    @Test
    @DisplayName("2.1 Odontólogo crea plan de tratamiento exitosamente (201)")
    void crearTratamiento_OdontologoAutorizado_Exitoso() throws Exception {
        UUID pacienteId = UUID.randomUUID();
        TratamientoCreateRequestDTO req = TratamientoCreateRequestDTO.builder()
                .pacienteId(pacienteId)
                .sedeId("sede-chilca-1")
                .especialistaId(odontologoId)
                .nombre("Endodoncia y Corona")
                .costoTotal(new BigDecimal("1200.00"))
                .modalidadPago("CONTADO")
                .build();

        TratamientoResponseDTO res = TratamientoResponseDTO.builder()
                .id(UUID.randomUUID())
                .pacienteId(pacienteId)
                .sedeId("sede-chilca-1")
                .nombre("Endodoncia y Corona")
                .costoTotal(new BigDecimal("1200.00"))
                .estado("PLANIFICADO")
                .build();

        when(tratamientoService.crear(any(TratamientoCreateRequestDTO.class))).thenReturn(res);

        mockMvc.perform(post("/tratamientos")
                        .header("Authorization", "Bearer " + tokenOdontologo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Endodoncia y Corona"))
                .andExpect(jsonPath("$.costoTotal").value(1200.00));
    }

    @Test
    @DisplayName("2.1 Recepción NO puede crear planes de tratamiento (403)")
    void crearTratamiento_RecepcionNoAutorizada_Retorna403() throws Exception {
        TratamientoCreateRequestDTO req = TratamientoCreateRequestDTO.builder()
                .pacienteId(UUID.randomUUID())
                .sedeId("sede-chilca-1")
                .especialistaId(odontologoId)
                .nombre("Curación simple")
                .costoTotal(new BigDecimal("80.00"))
                .build();

        mockMvc.perform(post("/tratamientos")
                        .header("Authorization", "Bearer " + tokenRecepcion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("2.1 Acceso cruzado en tratamientos lanza 403")
    void crearTratamiento_SedeCruzada_Retorna403() throws Exception {
        TratamientoCreateRequestDTO req = TratamientoCreateRequestDTO.builder()
                .pacienteId(UUID.randomUUID())
                .sedeId("sede-eltambo")
                .especialistaId(odontologoId)
                .nombre("Curación simple")
                .costoTotal(new BigDecimal("80.00"))
                .build();

        when(tratamientoService.crear(any(TratamientoCreateRequestDTO.class)))
                .thenThrow(new AccessDeniedException("Acceso Denegado: sede no corresponde"));

        mockMvc.perform(post("/tratamientos")
                        .header("Authorization", "Bearer " + tokenOdontologo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("2.1 Paciente acepta presupuesto a través de Recepción (200)")
    void aceptarPresupuesto_RecepcionAutorizada_Exitoso() throws Exception {
        UUID tratamientoId = UUID.randomUUID();
        TratamientoAceptacionRequestDTO req = new TratamientoAceptacionRequestDTO(true, "Aceptado verbalmente y firmado");

        TratamientoResponseDTO res = TratamientoResponseDTO.builder()
                .id(tratamientoId)
                .nombre("Ortodoncia Bracket Metálico")
                .aceptadoPorPaciente(true)
                .estado("EN_PROCESO")
                .build();

        when(tratamientoService.aceptarPresupuesto(eq(tratamientoId), any())).thenReturn(res);

        mockMvc.perform(post("/tratamientos/" + tratamientoId + "/aceptar")
                        .header("Authorization", "Bearer " + tokenRecepcion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aceptadoPorPaciente").value(true))
                .andExpect(jsonPath("$.estado").value("EN_PROCESO"));
    }

    @Test
    @DisplayName("2.1 Odontólogo NO puede eliminar planes de tratamiento (403)")
    void eliminarTratamiento_OdontologoNoAutorizado_Retorna403() throws Exception {
        UUID tratamientoId = UUID.randomUUID();
        mockMvc.perform(delete("/tratamientos/" + tratamientoId)
                        .header("Authorization", "Bearer " + tokenOdontologo))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("2.1 Odontólogo autorizado puede cerrar o actualizar plan de tratamiento (200)")
    void actualizarTratamiento_OdontologoAutorizado_Exitoso() throws Exception {
        UUID tratamientoId = UUID.randomUUID();
        TratamientoUpdateRequestDTO req = TratamientoUpdateRequestDTO.builder()
                .estado("TERMINADO")
                .motivoCierre("Tratamiento concluido con éxito")
                .build();

        TratamientoResponseDTO res = TratamientoResponseDTO.builder()
                .id(tratamientoId)
                .estado("TERMINADO")
                .motivoCierre("Tratamiento concluido con éxito")
                .build();

        when(tratamientoService.actualizar(eq(tratamientoId), any())).thenReturn(res);

        mockMvc.perform(patch("/tratamientos/" + tratamientoId)
                        .header("Authorization", "Bearer " + tokenOdontologo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("TERMINADO"));
    }

    @Test
    @DisplayName("2.1 Recepción NO puede cerrar o actualizar planes de tratamiento (403)")
    void actualizarTratamiento_RecepcionNoAutorizada_Retorna403() throws Exception {
        UUID tratamientoId = UUID.randomUUID();
        TratamientoUpdateRequestDTO req = TratamientoUpdateRequestDTO.builder()
                .estado("TERMINADO")
                .build();

        mockMvc.perform(patch("/tratamientos/" + tratamientoId)
                        .header("Authorization", "Bearer " + tokenRecepcion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 2. PAGOS Y COMPROBANTES (2.2)
    // =========================================================================

    @Test
    @DisplayName("2.2 Recepción registra cobro en efectivo y emite recibo (201)")
    void registrarPago_RecepcionAutorizada_Exitoso() throws Exception {
        UUID pacienteId = UUID.randomUUID();
        PagoCreateRequestDTO req = PagoCreateRequestDTO.builder()
                .pacienteId(pacienteId)
                .sedeId("sede-chilca-1")
                .monto(new BigDecimal("150.00"))
                .metodoPago("EFECTIVO")
                .comprobanteTipo("RECIBO")
                .build();

        PagoResponseDTO res = PagoResponseDTO.builder()
                .id(UUID.randomUUID())
                .pacienteId(pacienteId)
                .sedeId("sede-chilca-1")
                .monto(new BigDecimal("150.00"))
                .metodoPago("EFECTIVO")
                .comprobante("B001-00000001")
                .anulado(false)
                .build();

        when(pagoService.registrarPago(any())).thenReturn(res);

        mockMvc.perform(post("/pagos")
                        .header("Authorization", "Bearer " + tokenRecepcion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.monto").value(150.00))
                .andExpect(jsonPath("$.metodoPago").value("EFECTIVO"))
                .andExpect(jsonPath("$.comprobante").value("B001-00000001"));
    }

    @Test
    @DisplayName("2.2 Odontólogo cobra al finalizar la atención en sillón (clínica sin recepción) (201)")
    void registrarPago_OdontologoAutorizado_Exitoso() throws Exception {
        UUID pacienteId = UUID.randomUUID();
        PagoCreateRequestDTO req = PagoCreateRequestDTO.builder()
                .pacienteId(pacienteId)
                .sedeId("sede-chilca-1")
                .monto(new BigDecimal("100.00"))
                .metodoPago("YAPE")
                .build();

        PagoResponseDTO res = PagoResponseDTO.builder()
                .id(UUID.randomUUID())
                .pacienteId(pacienteId)
                .sedeId("sede-chilca-1")
                .monto(new BigDecimal("100.00"))
                .metodoPago("YAPE")
                .anulado(false)
                .build();

        when(pagoService.registrarPago(any())).thenReturn(res);

        mockMvc.perform(post("/pagos")
                        .header("Authorization", "Bearer " + tokenOdontologo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.metodoPago").value("YAPE"));
    }

    @Test
    @DisplayName("2.2 Odontólogo sigue sin poder anular pagos (403)")
    void anularPago_OdontologoNoAutorizado_Retorna403() throws Exception {
        PagoAnulacionRequestDTO req = new PagoAnulacionRequestDTO("Intento de anulación");

        mockMvc.perform(post("/pagos/" + UUID.randomUUID() + "/anular")
                        .header("Authorization", "Bearer " + tokenOdontologo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("2.2 Recepción NO puede anular pagos (solo ADMIN_SEDE/ADMIN_GENERAL) (403)")
    void anularPago_RecepcionNoAutorizada_Retorna403() throws Exception {
        UUID pagoId = UUID.randomUUID();
        PagoAnulacionRequestDTO req = new PagoAnulacionRequestDTO("Error de digitación");

        mockMvc.perform(post("/pagos/" + pagoId + "/anular")
                        .header("Authorization", "Bearer " + tokenRecepcion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("2.2 Admin Sede anula pago con motivo obligatorio (200)")
    void anularPago_AdminSedeAutorizado_Exitoso() throws Exception {
        UUID pagoId = UUID.randomUUID();
        PagoAnulacionRequestDTO req = new PagoAnulacionRequestDTO("Cobro duplicado por error de sistema");

        PagoResponseDTO res = PagoResponseDTO.builder()
                .id(pagoId)
                .monto(new BigDecimal("150.00"))
                .anulado(true)
                .motivoAnulacion("Cobro duplicado por error de sistema")
                .build();

        when(pagoService.anularPago(eq(pagoId), any())).thenReturn(res);

        mockMvc.perform(post("/pagos/" + pagoId + "/anular")
                        .header("Authorization", "Bearer " + tokenAdminSede)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anulado").value(true))
                .andExpect(jsonPath("$.motivoAnulacion").value("Cobro duplicado por error de sistema"));
    }

    // =========================================================================
    // 3. CAJA DIARIA Y ARQUEO (2.3)
    // =========================================================================

    @Test
    @DisplayName("2.3 Recepción abre turno de caja con fondo inicial (201)")
    void abrirCaja_RecepcionAutorizada_Exitoso() throws Exception {
        CajaAperturaRequestDTO req = CajaAperturaRequestDTO.builder()
                .sedeId("sede-chilca-1")
                .montoApertura(new BigDecimal("100.00"))
                .observaciones("Fondo sencillo billetes y monedas")
                .build();

        CajaSesionResponseDTO res = CajaSesionResponseDTO.builder()
                .id(UUID.randomUUID())
                .sedeId("sede-chilca-1")
                .montoApertura(new BigDecimal("100.00"))
                .estado("ABIERTA")
                .build();

        when(cajaService.abrirCaja(any())).thenReturn(res);

        mockMvc.perform(post("/caja/abrir")
                        .header("Authorization", "Bearer " + tokenRecepcion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.montoApertura").value(100.00))
                .andExpect(jsonPath("$.estado").value("ABIERTA"));
    }

    @Test
    @DisplayName("2.3 Odontólogo puede consultar si la caja está abierta (el servicio le devuelve solo el estado)")
    void cajaActiva_OdontologoConsultaEstado_Retorna200() throws Exception {
        when(cajaService.obtenerSesionActiva(any())).thenReturn(Optional.of(
                CajaSesionResponseDTO.builder().id(UUID.randomUUID()).sedeId("sede-chilca-1").estado("ABIERTA").build()));

        mockMvc.perform(get("/caja/activa?sedeId=sede-chilca-1")
                        .header("Authorization", "Bearer " + tokenOdontologo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ABIERTA"));
    }

    @Test
    @DisplayName("2.3 Odontólogo no puede abrir la caja de la sede (403)")
    void abrirCaja_OdontologoNoAutorizado_Retorna403() throws Exception {
        CajaAperturaRequestDTO req = CajaAperturaRequestDTO.builder()
                .sedeId("sede-chilca-1")
                .montoApertura(new BigDecimal("50.00"))
                .build();

        // Apertura de caja reservada a administración y recepción
        mockMvc.perform(post("/caja/abrir")
                        .header("Authorization", "Bearer " + tokenOdontologo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("2.3 Odontólogo no puede cerrar la caja de la sede (403)")
    void cerrarCaja_OdontologoNoAutorizado_Retorna403() throws Exception {
        UUID sesionId = UUID.randomUUID();
        CajaCierreRequestDTO req = CajaCierreRequestDTO.builder()
                .montoContado(new BigDecimal("100.00"))
                .build();

        // Cierre de caja y arqueo reservado a administración y recepción
        mockMvc.perform(post("/caja/" + sesionId + "/cerrar")
                        .header("Authorization", "Bearer " + tokenOdontologo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("2.3 Odontólogo recibe 403 en las 4 rutas de /caja/* que muestran o mueven dinero")
    void caja_OdontologoRecibe403EnCuatroRutas() throws Exception {
        UUID sesionId = UUID.randomUUID();

        // 2. GET /caja/{id}
        mockMvc.perform(get("/caja/" + sesionId)
                        .header("Authorization", "Bearer " + tokenOdontologo))
                .andExpect(status().isForbidden());

        // 3. POST /caja/abrir
        mockMvc.perform(post("/caja/abrir")
                        .header("Authorization", "Bearer " + tokenOdontologo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CajaAperturaRequestDTO.builder()
                                .sedeId("sede-chilca-1").montoApertura(BigDecimal.TEN).build())))
                .andExpect(status().isForbidden());

        // 4. POST /caja/{id}/movimientos
        mockMvc.perform(post("/caja/" + sesionId + "/movimientos")
                        .header("Authorization", "Bearer " + tokenOdontologo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CajaMovimientoRequestDTO.builder()
                                .tipo("INGRESO").concepto("Ajuste").monto(BigDecimal.TEN).build())))
                .andExpect(status().isForbidden());

        // 5. POST /caja/{id}/cerrar
        mockMvc.perform(post("/caja/" + sesionId + "/cerrar")
                        .header("Authorization", "Bearer " + tokenOdontologo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CajaCierreRequestDTO.builder()
                                .montoContado(BigDecimal.TEN).build())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("2.3 Recepción, Admin Sede y Admin General siguen con acceso a las 5 rutas de /caja/*")
    void caja_RolesAdministrativosSiguenConAcceso() throws Exception {
        UUID sesionId = UUID.randomUUID();
        CajaSesionResponseDTO sesion = CajaSesionResponseDTO.builder()
                .id(sesionId).sedeId("sede-chilca-1").estado("ABIERTA").build();
        when(cajaService.obtenerSesionActiva(any())).thenReturn(Optional.of(sesion));
        when(cajaService.obtenerPorId(eq(sesionId))).thenReturn(sesion);
        when(cajaService.abrirCaja(any())).thenReturn(sesion);
        when(cajaService.registrarMovimientoManual(eq(sesionId), any())).thenReturn(
                CajaMovimientoResponseDTO.builder().id(UUID.randomUUID()).tipo("INGRESO").monto(BigDecimal.TEN).build());
        when(cajaService.cerrarCaja(eq(sesionId), any())).thenReturn(sesion);

        List<String> tokens = List.of(tokenRecepcion, tokenAdminSede, tokenAdminGeneral);

        for (String token : tokens) {
            // GET /caja/activa
            mockMvc.perform(get("/caja/activa?sedeId=sede-chilca-1")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());

            // GET /caja/{id}
            mockMvc.perform(get("/caja/" + sesionId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());

            // POST /caja/abrir
            mockMvc.perform(post("/caja/abrir")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(CajaAperturaRequestDTO.builder()
                                    .sedeId("sede-chilca-1").montoApertura(BigDecimal.TEN).build())))
                    .andExpect(status().isCreated());

            // POST /caja/{id}/movimientos
            mockMvc.perform(post("/caja/" + sesionId + "/movimientos")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(CajaMovimientoRequestDTO.builder()
                                    .tipo("INGRESO").concepto("Ajuste").monto(BigDecimal.TEN).build())))
                    .andExpect(status().isCreated());

            // POST /caja/{id}/cerrar
            mockMvc.perform(post("/caja/" + sesionId + "/cerrar")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(CajaCierreRequestDTO.builder()
                                    .montoContado(BigDecimal.TEN).build())))
                    .andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("2.3 Odontólogo sigue sin ver el historial de cajas (403)")
    void historialCaja_OdontologoNoAutorizado_Retorna403() throws Exception {
        mockMvc.perform(get("/caja/historial?sedeId=sede-chilca-1")
                        .header("Authorization", "Bearer " + tokenOdontologo))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("2.3 Cierre de caja con arqueo y registro de diferencia (200)")
    void cerrarCaja_ArqueoConDiferencia_Exitoso() throws Exception {
        UUID sesionId = UUID.randomUUID();
        CajaCierreRequestDTO req = CajaCierreRequestDTO.builder()
                .montoContado(new BigDecimal("350.00"))
                .observaciones("Cuadre conforme al cierre de turno")
                .build();

        CajaSesionResponseDTO res = CajaSesionResponseDTO.builder()
                .id(sesionId)
                .sedeId("sede-chilca-1")
                .montoEsperado(new BigDecimal("350.00"))
                .montoContado(new BigDecimal("350.00"))
                .diferencia(BigDecimal.ZERO)
                .estado("CERRADA")
                .build();

        when(cajaService.cerrarCaja(eq(sesionId), any())).thenReturn(res);

        mockMvc.perform(post("/caja/" + sesionId + "/cerrar")
                        .header("Authorization", "Bearer " + tokenRecepcion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.montoEsperado").value(350.00))
                .andExpect(jsonPath("$.montoContado").value(350.00))
                .andExpect(jsonPath("$.diferencia").value(0.00))
                .andExpect(jsonPath("$.estado").value("CERRADA"));
    }

    @Test
    @DisplayName("2.3 Recepción puede consultar el historial de sesiones de caja")
    void historialCaja_RecepcionAutorizada_Retorna200() throws Exception {
        when(cajaService.listarSesionesPorSede("sede-chilca-1")).thenReturn(List.of());
        mockMvc.perform(get("/caja/historial?sedeId=sede-chilca-1")
                        .header("Authorization", "Bearer " + tokenRecepcion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // =========================================================================
    // 4. GASTOS (2.4)
    // =========================================================================

    @Test
    @DisplayName("2.4 Recepción registra egreso/gasto en sede autorizada (201)")
    void registrarGasto_RecepcionAutorizada_Exitoso() throws Exception {
        GastoCreateRequestDTO req = GastoCreateRequestDTO.builder()
                .sedeId("sede-chilca-1")
                .categoria("MATERIALES")
                .concepto("Compra de guantes y mascarillas")
                .monto(new BigDecimal("45.00"))
                .metodoPago("EFECTIVO")
                .build();

        GastoResponseDTO res = GastoResponseDTO.builder()
                .id(UUID.randomUUID())
                .sedeId("sede-chilca-1")
                .categoria("MATERIALES")
                .concepto("Compra de guantes y mascarillas")
                .monto(new BigDecimal("45.00"))
                .anulado(false)
                .build();

        when(gastoService.registrarGasto(any())).thenReturn(res);

        mockMvc.perform(post("/gastos")
                        .header("Authorization", "Bearer " + tokenRecepcion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.monto").value(45.00))
                .andExpect(jsonPath("$.categoria").value("MATERIALES"));
    }

    // =========================================================================
    // 5. REPORTES FINANCIEROS Y DEUDORES (2.5)
    // =========================================================================

    @Test
    @DisplayName("2.5 Recepción NO puede ver reportes financieros (403)")
    void reporteFinanciero_RecepcionNoAutorizada_Retorna403() throws Exception {
        mockMvc.perform(get("/reportes/financiero?sedeId=sede-chilca-1")
                        .header("Authorization", "Bearer " + tokenRecepcion))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("2.5 Admin Sede consulta reporte financiero (200)")
    void reporteFinanciero_AdminSedeAutorizado_Exitoso() throws Exception {
        ReporteFinancieroResponseDTO res = ReporteFinancieroResponseDTO.builder()
                .sedeId("sede-chilca-1")
                .sedeNombre("Sede Chilca 1 (Principal)")
                .fechaInicio(LocalDate.now().withDayOfMonth(1))
                .fechaFin(LocalDate.now())
                .totalIngresos(new BigDecimal("5000.00"))
                .totalGastos(new BigDecimal("1200.00"))
                .balanceNeto(new BigDecimal("3800.00"))
                .totalEfectivo(new BigDecimal("3000.00"))
                .totalYape(new BigDecimal("2000.00"))
                .build();

        when(reporteService.obtenerReporteFinanciero(eq("sede-chilca-1"), any(), any())).thenReturn(res);

        mockMvc.perform(get("/reportes/financiero?sedeId=sede-chilca-1")
                        .header("Authorization", "Bearer " + tokenAdminSede))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalIngresos").value(5000.00))
                .andExpect(jsonPath("$.totalGastos").value(1200.00))
                .andExpect(jsonPath("$.balanceNeto").value(3800.00));
    }

    @Test
    @DisplayName("2.5 Admin Sede consulta reporte de deudores (200)")
    void reporteDeudores_AdminSedeAutorizado_Exitoso() throws Exception {
        ReporteDeudoresResponseDTO res = ReporteDeudoresResponseDTO.builder()
                .sedeId("sede-chilca-1")
                .deudaTotalAcumulada(new BigDecimal("850.00"))
                .totalPacientesDeudores(2L)
                .build();

        when(reporteService.obtenerReporteDeudores(eq("sede-chilca-1"))).thenReturn(res);

        mockMvc.perform(get("/reportes/deudores?sedeId=sede-chilca-1")
                        .header("Authorization", "Bearer " + tokenAdminSede))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deudaTotalAcumulada").value(850.00))
                .andExpect(jsonPath("$.totalPacientesDeudores").value(2));
    }

    @Test
    @DisplayName("2.5 Admin Sede consulta reporte de rendimiento de citas (200)")
    void reporteCitasRendimiento_AdminSedeAutorizado_Exitoso() throws Exception {
        ReporteCitasRendimientoResponseDTO res = ReporteCitasRendimientoResponseDTO.builder()
                .sedeId("sede-chilca-1")
                .totalCitas(20L)
                .citasAtendidas(16L)
                .citasCanceladas(2L)
                .citasNoAsistio(2L)
                .tasaAsistencia(80.0)
                .build();

        when(reporteService.obtenerReporteCitasRendimiento(eq("sede-chilca-1"), any(), any())).thenReturn(res);

        mockMvc.perform(get("/reportes/citas-rendimiento?sedeId=sede-chilca-1")
                        .header("Authorization", "Bearer " + tokenAdminSede))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCitas").value(20))
                .andExpect(jsonPath("$.citasAtendidas").value(16))
                .andExpect(jsonPath("$.tasaAsistencia").value(80.0));
    }
}
