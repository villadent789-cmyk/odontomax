package com.odontomax.caja;

import com.odontomax.application.dto.request.CajaCierreRequestDTO;
import com.odontomax.application.dto.request.CajaAperturaRequestDTO;
import com.odontomax.application.dto.request.CajaMovimientoRequestDTO;
import com.odontomax.application.dto.response.CajaMovimientoResponseDTO;
import com.odontomax.application.port.out.CajaPeriodoPort;
import com.odontomax.application.port.out.CajaTotalesSesion;
import com.odontomax.infrastructure.persistence.entity.CajaMovimientoEntity;
import com.odontomax.infrastructure.persistence.entity.CajaSesionEntity;
import com.odontomax.infrastructure.persistence.repository.SpringDataAuditoriaRepository;
import com.odontomax.infrastructure.persistence.repository.SpringDataCajaMovimientoRepository;
import com.odontomax.infrastructure.persistence.repository.SpringDataCajaSesionRepository;
import com.odontomax.infrastructure.persistence.repository.SpringDataPagoRepository;
import com.odontomax.domain.exception.DiferenciaCajaSinObservacionesException;
import com.odontomax.domain.exception.IngresoManualCajaNoPermitidoException;
import com.odontomax.identidad.api.ClinicaUserPrincipal;
import com.odontomax.identidad.api.ConsultaUsuarioModuloApi;
import com.odontomax.application.service.CajaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class CajaServiceTest {

    @Mock
    private SpringDataCajaSesionRepository cajaSesionRepository;

    @Mock
    private SpringDataCajaMovimientoRepository cajaMovimientoRepository;

    @Mock
    private SpringDataPagoRepository pagoRepository;

    @Mock
    private SpringDataAuditoriaRepository auditoriaRepository;

    @Mock
    private CajaPeriodoPort cajaPeriodoPort;

    @Mock
    private ConsultaUsuarioModuloApi consultaUsuarioModuloApi;

    @InjectMocks
    private CajaService cajaService;

    private UUID userId;
    private UUID sesionId;
    private CajaSesionEntity sesionAbierta;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        sesionId = UUID.randomUUID();

        sesionAbierta = CajaSesionEntity.builder()
                .id(sesionId)
                .sedeId("sede-chilca-1")
                .abiertaPor(userId)
                .cerradaEn(null)
                .build();

        cajaService.setCajaPeriodoPort(cajaPeriodoPort);
        cajaService.setConsultaUsuarioModuloApi(consultaUsuarioModuloApi);
        cajaService.setTopes(new BigDecimal("200.00"), new BigDecimal("500.00"), new BigDecimal("1000.00"));
        lenient().when(cajaPeriodoPort.calcularTotales(any())).thenReturn(totales("0", "0", "0", "0", "0", "0"));
        lenient().when(consultaUsuarioModuloApi.buscarPorId(any())).thenReturn(Optional.empty());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateUser(String rol, String sedeId) {
        ClinicaUserPrincipal principal = new ClinicaUserPrincipal(
                userId, "user@odontomax.pe", "Usuario Prueba", rol, sedeId
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, null)
        );
    }

    @Test
    @DisplayName("Egreso manual: supera tope por movimiento individual (S/ 200) por RECEPCION -> 403 Forbidden")
    void egresoManual_superaTopeMovimiento_lanza403() {
        authenticateUser("RECEPCION", "sede-chilca-1");
        when(cajaSesionRepository.findById(sesionId)).thenReturn(Optional.of(sesionAbierta));

        CajaMovimientoRequestDTO dto = CajaMovimientoRequestDTO.builder()
                .tipo("EGRESO")
                .concepto("Compra de suministros varios")
                .monto(new BigDecimal("250.00")) // Supera S/ 200.00
                .build();

        assertThatThrownBy(() -> cajaService.registrarMovimientoManual(sesionId, dto))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("mayores a S/ 200.00 requieren autorización");
    }

    @Test
    @DisplayName("Egreso manual: supera tope diario por usuario (S/ 500) por RECEPCION -> 403 Forbidden")
    void egresoManual_superaTopeDiarioUsuario_lanza403() {
        authenticateUser("RECEPCION", "sede-chilca-1");
        when(cajaSesionRepository.findById(sesionId)).thenReturn(Optional.of(sesionAbierta));
        when(cajaMovimientoRepository.sumEgresosManualesUsuarioYDia(eq(userId), eq("sede-chilca-1"), any(Instant.class)))
                .thenReturn(new BigDecimal("400.00")); // Ya gastó 400 hoy

        CajaMovimientoRequestDTO dto = CajaMovimientoRequestDTO.builder()
                .tipo("EGRESO")
                .concepto("Pago de mensajería")
                .monto(new BigDecimal("150.00")) // 400 + 150 = 550 > 500
                .build();

        assertThatThrownBy(() -> cajaService.registrarMovimientoManual(sesionId, dto))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("tope diario permitido de egresos manuales por usuario (S/ 500.00)");
    }

    @Test
    @DisplayName("Egreso manual: supera tope diario global de la sede (S/ 1000) por ADMIN_SEDE -> 403 Forbidden")
    void egresoManual_superaTopeDiarioSede_adminSedeLanza403() {
        authenticateUser("ADMIN_SEDE", "sede-chilca-1");
        when(cajaSesionRepository.findById(sesionId)).thenReturn(Optional.of(sesionAbierta));
        when(cajaMovimientoRepository.sumEgresosManualesUsuarioYDia(eq(userId), eq("sede-chilca-1"), any(Instant.class)))
                .thenReturn(new BigDecimal("100.00"));
        when(cajaMovimientoRepository.sumEgresosManualesSedeYDia(eq("sede-chilca-1"), any(Instant.class)))
                .thenReturn(new BigDecimal("900.00")); // La sede ya gastó 900 hoy

        CajaMovimientoRequestDTO dto = CajaMovimientoRequestDTO.builder()
                .tipo("EGRESO")
                .concepto("Reparación mayor compresor dental")
                .monto(new BigDecimal("200.00")) // 900 + 200 = 1100 > 1000
                .build();

        assertThatThrownBy(() -> cajaService.registrarMovimientoManual(sesionId, dto))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("tope diario permitido de egresos manuales para la sede (S/ 1000.00)");
    }

    @Test
    @DisplayName("Egreso manual: ADMIN_GENERAL puede autorizar y registrar superando el tope de sede")
    void egresoManual_adminGeneral_superaTopes_exitoso() {
        authenticateUser("ADMIN_GENERAL", null);
        when(cajaSesionRepository.findById(sesionId)).thenReturn(Optional.of(sesionAbierta));
        when(cajaMovimientoRepository.sumEgresosManualesUsuarioYDia(eq(userId), eq("sede-chilca-1"), any(Instant.class)))
                .thenReturn(new BigDecimal("1000.00"));
        when(cajaMovimientoRepository.sumEgresosManualesSedeYDia(eq("sede-chilca-1"), any(Instant.class)))
                .thenReturn(new BigDecimal("2000.00"));

        CajaMovimientoEntity movGuardado = CajaMovimientoEntity.builder()
                .id(UUID.randomUUID())
                .sesion(sesionAbierta)
                .tipo("EGRESO")
                .concepto("Gasto excepcional de gerencia")
                .monto(new BigDecimal("1500.00"))
                .metodoPago("EFECTIVO")
                .referenciaTipo("AJUSTE_MANUAL")
                .registradoPor(userId)
                .build();

        when(cajaMovimientoRepository.save(any())).thenReturn(movGuardado);

        CajaMovimientoRequestDTO dto = CajaMovimientoRequestDTO.builder()
                .tipo("EGRESO")
                .concepto("Gasto excepcional de gerencia")
                .monto(new BigDecimal("1500.00"))
                .build();

        CajaMovimientoResponseDTO res = cajaService.registrarMovimientoManual(sesionId, dto);
        assertThat(res.getMonto()).isEqualTo(new BigDecimal("1500.00"));
        assertThat(res.getTipo()).isEqualTo("EGRESO");
    }

    @Test
    @DisplayName("Ingreso manual es rechazado")
    void ingresoManual_rechazado() {
        authenticateUser("RECEPCION", "sede-chilca-1");
        when(cajaSesionRepository.findById(sesionId)).thenReturn(Optional.of(sesionAbierta));
        CajaMovimientoRequestDTO dto = CajaMovimientoRequestDTO.builder()
                .tipo("INGRESO").concepto("Ingreso adicional").monto(BigDecimal.TEN).build();

        assertThatThrownBy(() -> cajaService.registrarMovimientoManual(sesionId, dto))
                .isInstanceOf(IngresoManualCajaNoPermitidoException.class)
                .hasMessageContaining("No se permiten ingresos manuales");
    }

    @Test
    @DisplayName("Apertura ignora el fondo solicitado y siempre guarda cero")
    void abrirCaja_guardaCeroAunqueSolicitudIndiqueFondo() {
        authenticateUser("ADMIN_GENERAL", null);
        when(cajaSesionRepository.findBySedeIdAndAbiertaPorAndCerradaEnIsNull("sede-chilca-1", userId))
                .thenReturn(Optional.empty());
        when(cajaSesionRepository.findBySedeIdAndCerradaEnIsNull("sede-chilca-1")).thenReturn(Optional.empty());
        when(cajaSesionRepository.save(any(CajaSesionEntity.class))).thenAnswer(invocation -> {
            CajaSesionEntity guardada = invocation.getArgument(0);
            guardada.setId(sesionId);
            return guardada;
        });
        when(cajaMovimientoRepository.findBySesionIdOrderByRegistradoEnAsc(sesionId)).thenReturn(java.util.List.of());
        cajaService.abrirCaja(CajaAperturaRequestDTO.builder()
                .sedeId("sede-chilca-1")
                .montoApertura(new BigDecimal("250.00"))
                .build());

        verify(cajaSesionRepository).save(org.mockito.ArgumentMatchers.argThat(
                caja -> caja.getMontoApertura().compareTo(BigDecimal.ZERO) == 0));
    }

    @Test
    @DisplayName("Egreso manual: dentro de los topes permitidos para RECEPCION -> exitoso")
    void egresoManual_dentroDeTopes_exitoso() {
        authenticateUser("RECEPCION", "sede-chilca-1");
        when(cajaSesionRepository.findById(sesionId)).thenReturn(Optional.of(sesionAbierta));
        when(cajaMovimientoRepository.sumEgresosManualesUsuarioYDia(eq(userId), eq("sede-chilca-1"), any(Instant.class)))
                .thenReturn(BigDecimal.ZERO);
        when(cajaMovimientoRepository.sumEgresosManualesSedeYDia(eq("sede-chilca-1"), any(Instant.class)))
                .thenReturn(BigDecimal.ZERO);

        CajaMovimientoEntity movGuardado = CajaMovimientoEntity.builder()
                .id(UUID.randomUUID())
                .sesion(sesionAbierta)
                .tipo("EGRESO")
                .concepto("Compra de papel higiénico y jabón")
                .monto(new BigDecimal("35.00"))
                .metodoPago("EFECTIVO")
                .referenciaTipo("AJUSTE_MANUAL")
                .registradoPor(userId)
                .build();

        when(cajaMovimientoRepository.save(any())).thenReturn(movGuardado);

        CajaMovimientoRequestDTO dto = CajaMovimientoRequestDTO.builder()
                .tipo("EGRESO")
                .concepto("Compra de papel higiénico y jabón")
                .monto(new BigDecimal("35.00"))
                .build();

        CajaMovimientoResponseDTO res = cajaService.registrarMovimientoManual(sesionId, dto);
        assertThat(res.getMonto()).isEqualTo(new BigDecimal("35.00"));
    }

    @Test
    @DisplayName("Zona Horaria Lima: A las 19:00 Lima (00:00 UTC sig. día), inicioDia pertenece al día local de Lima")
    void inicioDia_aLas19HorasLima_perteneceAlMismoDiaLocalLima() {
        // 2026-10-01 19:00:00 en Lima (UTC-5) equivale a 2026-10-02 00:00:00 UTC
        Instant instante19pmLima = Instant.parse("2026-10-02T00:00:00Z");
        cajaService.setClock(Clock.fixed(instante19pmLima, CajaService.ZONE_LIMA));

        Instant inicioDia = cajaService.calcularInicioDiaLima();
        // El inicio del día 2026-10-01 en Lima es 2026-10-01 00:00:00 Lima = 2026-10-01 05:00:00 UTC
        Instant inicioDiaEsperado = Instant.parse("2026-10-01T05:00:00Z");

        assertThat(inicioDia).isEqualTo(inicioDiaEsperado);
    }

    @Test
    @DisplayName("Zona Horaria Lima: A las 00:00 Lima, inicioDia corresponde exactamente al nuevo día en Lima")
    void inicioDia_aLas00HorasLima_iniciaNuevoDiaLocalLima() {
        // 2026-10-02 00:00:00 en Lima (UTC-5) equivale a 2026-10-02 05:00:00 UTC
        Instant instante00amLima = Instant.parse("2026-10-02T05:00:00Z");
        cajaService.setClock(Clock.fixed(instante00amLima, CajaService.ZONE_LIMA));

        Instant inicioDia = cajaService.calcularInicioDiaLima();
        // El inicio del día 2026-10-02 en Lima es 2026-10-02 00:00:00 Lima = 2026-10-02 05:00:00 UTC
        Instant inicioDiaEsperado = Instant.parse("2026-10-02T05:00:00Z");

        assertThat(inicioDia).isEqualTo(inicioDiaEsperado);
    }

    @Test
    @DisplayName("Egreso manual a las 19:00 Lima: consulta acumulado con inicio de día de Lima y aplica tope si se supera")
    void egresoManual_aLas19HorasLima_aplicaTopeDiarioConInicioDiaLima() {
        authenticateUser("RECEPCION", "sede-chilca-1");

        // Simular las 19:00 hora de Lima (00:00 UTC del día siguiente)
        Instant instante19pmLima = Instant.parse("2026-10-02T00:00:00Z");
        Instant inicioDiaLima = Instant.parse("2026-10-01T05:00:00Z");
        cajaService.setClock(Clock.fixed(instante19pmLima, CajaService.ZONE_LIMA));

        when(cajaSesionRepository.findById(sesionId)).thenReturn(Optional.of(sesionAbierta));
        // Repositorio recibe exactamente el inicio del día en Lima
        when(cajaMovimientoRepository.sumEgresosManualesUsuarioYDia(eq(userId), eq("sede-chilca-1"), eq(inicioDiaLima)))
                .thenReturn(new BigDecimal("420.00"));

        CajaMovimientoRequestDTO dto = CajaMovimientoRequestDTO.builder()
                .tipo("EGRESO")
                .concepto("Pago de delivery nocturno")
                .monto(new BigDecimal("100.00")) // 420 + 100 = 520 > 500
                .build();

        assertThatThrownBy(() -> cajaService.registrarMovimientoManual(sesionId, dto))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("tope diario permitido de egresos manuales por usuario (S/ 500.00)");
    }

    @Test
    @DisplayName("Egreso manual a las 00:00 Lima: nuevo día permite egreso dentro del nuevo acumulado diario")
    void egresoManual_aLas00HorasLima_nuevoDiaPermiteEgreso() {
        authenticateUser("RECEPCION", "sede-chilca-1");

        // Simular exactamente las 00:00:00 hora de Lima (05:00:00 UTC)
        Instant instante00amLima = Instant.parse("2026-10-02T05:00:00Z");
        Instant inicioDiaLima = Instant.parse("2026-10-02T05:00:00Z");
        cajaService.setClock(Clock.fixed(instante00amLima, CajaService.ZONE_LIMA));

        when(cajaSesionRepository.findById(sesionId)).thenReturn(Optional.of(sesionAbierta));
        when(cajaMovimientoRepository.sumEgresosManualesUsuarioYDia(eq(userId), eq("sede-chilca-1"), eq(inicioDiaLima)))
                .thenReturn(BigDecimal.ZERO);
        when(cajaMovimientoRepository.sumEgresosManualesSedeYDia(eq("sede-chilca-1"), eq(inicioDiaLima)))
                .thenReturn(BigDecimal.ZERO);

        CajaMovimientoEntity movGuardado = CajaMovimientoEntity.builder()
                .id(UUID.randomUUID())
                .sesion(sesionAbierta)
                .tipo("EGRESO")
                .concepto("Gasto de inicio de jornada")
                .monto(new BigDecimal("80.00"))
                .metodoPago("EFECTIVO")
                .referenciaTipo("AJUSTE_MANUAL")
                .registradoPor(userId)
                .build();

        when(cajaMovimientoRepository.save(any())).thenReturn(movGuardado);

        CajaMovimientoRequestDTO dto = CajaMovimientoRequestDTO.builder()
                .tipo("EGRESO")
                .concepto("Gasto de inicio de jornada")
                .monto(new BigDecimal("80.00"))
                .build();

        CajaMovimientoResponseDTO res = cajaService.registrarMovimientoManual(sesionId, dto);
        assertThat(res.getMonto()).isEqualTo(new BigDecimal("80.00"));
    }

    @Test
    @DisplayName("Apertura automática: reutiliza la caja abierta de la sede o abre una con fondo S/ 0.00 y la audita")
    void obtenerOAbrirSesionAutomatica() {
        authenticateUser("ODONTOLOGO", "sede-chilca-1");
        when(cajaSesionRepository.findBySedeIdAndAbiertaPorAndCerradaEnIsNull("sede-chilca-1", userId)).thenReturn(Optional.empty());
        when(cajaSesionRepository.findBySedeIdAndCerradaEnIsNull("sede-chilca-1")).thenReturn(Optional.of(sesionAbierta));

        assertThat(cajaService.obtenerOAbrirSesionAutomatica("sede-chilca-1")).isSameAs(sesionAbierta);
        verify(cajaSesionRepository, never()).save(any());

        when(cajaSesionRepository.findBySedeIdAndCerradaEnIsNull("sede-chilca-1")).thenReturn(Optional.empty());
        when(cajaSesionRepository.save(any(CajaSesionEntity.class))).thenAnswer(inv -> {
            CajaSesionEntity nueva = inv.getArgument(0);
            nueva.setId(UUID.randomUUID());
            return nueva;
        });

        CajaSesionEntity nueva = cajaService.obtenerOAbrirSesionAutomatica("sede-chilca-1");

        assertThat(nueva.getMontoApertura()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(nueva.getAbiertaPor()).isEqualTo(userId);
        assertThat(nueva.getSedeId()).isEqualTo("sede-chilca-1");
        verify(auditoriaRepository).save(org.mockito.ArgumentMatchers.argThat(a -> "APERTURA_CAJA_AUTOMATICA".equals(a.getAccion())));
        verify(cajaMovimientoRepository, never()).save(any());
    }

    // ------------------------------------------------------------ cierre por el odontólogo de la sede

    private CajaSesionEntity sesionAbiertaPorOtro() {
        return CajaSesionEntity.builder().id(sesionId).sedeId("sede-chilca-1")
                .abiertaPor(UUID.randomUUID()).montoApertura(BigDecimal.ZERO).build();
    }

    @Test
    @DisplayName("Cierre: un odontólogo de la sede cierra la caja aunque la haya abierto otro usuario")
    void cerrarCaja_OdontologoDeLaSede_Permitido() {
        authenticateUser("ODONTOLOGO", "sede-chilca-1");
        when(cajaSesionRepository.findById(sesionId)).thenReturn(Optional.of(sesionAbiertaPorOtro()));
        when(cajaMovimientoRepository.findBySesionIdOrderByRegistradoEnAsc(sesionId)).thenReturn(java.util.List.of(
                CajaMovimientoEntity.builder().id(UUID.randomUUID()).sesion(sesionAbiertaPorOtro())
                        .tipo("INGRESO").metodoPago("EFECTIVO").monto(new BigDecimal("80.00")).build()));
        when(cajaPeriodoPort.calcularTotales(sesionId)).thenReturn(totales("80", "0", "0", "0", "0", "0"));
        when(cajaSesionRepository.save(any(CajaSesionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        var res = cajaService.cerrarCaja(sesionId, CajaCierreRequestDTO.builder().montoContado(new BigDecimal("80.00")).build());

        assertThat(res.getEstado()).isEqualTo("CERRADA");
        assertThat(res.getMontoContado()).isEqualByComparingTo("80.00");
    }

    @Test
    @DisplayName("Cierre calcula esperado y diferencia independientemente por método")
    void cerrarCaja_calculaTotalesPorMetodo() {
        authenticateUser("RECEPCION", "sede-chilca-1");
        when(cajaSesionRepository.findById(sesionId)).thenReturn(Optional.of(sesionAbierta));
        when(cajaSesionRepository.save(any(CajaSesionEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(cajaMovimientoRepository.findBySesionIdOrderByRegistradoEnAsc(sesionId)).thenReturn(java.util.List.of(
                movimiento("EFECTIVO", "INGRESO", "100.00"),
                movimiento("EFECTIVO", "EGRESO", "10.00"),
                movimiento("YAPE", "INGRESO", "50.00"),
                movimiento("PLIN", "INGRESO", "70.00"),
                movimiento("PLIN", "EGRESO", "20.00")));
        when(cajaPeriodoPort.calcularTotales(sesionId)).thenReturn(
                totales("100", "10", "50", "0", "70", "20"),
                totalesCerrados("100", "10", "50", "0", "70", "20", "50", "60"));

        var respuesta = cajaService.cerrarCaja(sesionId, CajaCierreRequestDTO.builder()
                .montoContado(new BigDecimal("90.00"))
                .montoContadoYape(new BigDecimal("50.00"))
                .montoContadoPlin(new BigDecimal("60.00"))
                .observaciones("Diferencia de S/ 10 en Plin")
                .build());

        assertThat(respuesta.getMontoEsperado()).isEqualByComparingTo("90.00");
        assertThat(respuesta.getMontoEsperadoYape()).isEqualByComparingTo("50.00");
        assertThat(respuesta.getMontoEsperadoPlin()).isEqualByComparingTo("50.00");
        assertThat(respuesta.getDiferenciaPlin()).isEqualByComparingTo("10.00");
    }

    @Test
    @DisplayName("Cierre con diferencia sin observaciones se rechaza")
    void cerrarCaja_conDiferenciaSinObservaciones_rechazado() {
        authenticateUser("RECEPCION", "sede-chilca-1");
        when(cajaSesionRepository.findById(sesionId)).thenReturn(Optional.of(sesionAbierta));
        when(cajaPeriodoPort.calcularTotales(sesionId)).thenReturn(totales("0", "0", "0", "0", "0", "0"));

        assertThatThrownBy(() -> cajaService.cerrarCaja(sesionId, CajaCierreRequestDTO.builder()
                .montoContado(BigDecimal.ZERO).montoContadoPlin(BigDecimal.TEN).build()))
                .isInstanceOf(DiferenciaCajaSinObservacionesException.class);
        verify(cajaSesionRepository, never()).save(any());
    }

    private CajaMovimientoEntity movimiento(String metodo, String tipo, String monto) {
        return CajaMovimientoEntity.builder().id(UUID.randomUUID()).sesion(sesionAbierta)
                .metodoPago(metodo).tipo(tipo).monto(new BigDecimal(monto)).build();
    }

    @Test
    @DisplayName("Cierre cuadra juntas las tarjetas de débito y de crédito contra el POS")
    void cerrarCaja_cuadraTarjetasJuntas() {
        authenticateUser("RECEPCION", "sede-chilca-1");
        when(cajaSesionRepository.findById(sesionId)).thenReturn(Optional.of(sesionAbierta));
        BigDecimal z = BigDecimal.ZERO;
        when(cajaPeriodoPort.calcularTotales(sesionId)).thenReturn(new CajaTotalesSesion(
                z, z, z, z, z, z, z, null, null, z, null, null,
                new BigDecimal("80.00"), new BigDecimal("120.00"), z, new BigDecimal("200.00"), null, null));

        // Falta S/ 10 respecto de los S/ 200 (80 débito + 120 crédito): sin observaciones no se cierra.
        assertThatThrownBy(() -> cajaService.cerrarCaja(sesionId, CajaCierreRequestDTO.builder()
                .montoContado(BigDecimal.ZERO).montoContadoTarjeta(new BigDecimal("190.00")).build()))
                .isInstanceOf(DiferenciaCajaSinObservacionesException.class);
        verify(cajaSesionRepository, never()).save(any());
    }

    private CajaTotalesSesion totales(String efectivoIngreso, String efectivoEgreso,
                                      String yapeIngreso, String yapeEgreso,
                                      String plinIngreso, String plinEgreso) {
        BigDecimal esperadoYape = new BigDecimal(yapeIngreso).subtract(new BigDecimal(yapeEgreso));
        BigDecimal esperadoPlin = new BigDecimal(plinIngreso).subtract(new BigDecimal(plinEgreso));
        return new CajaTotalesSesion(new BigDecimal(efectivoIngreso), new BigDecimal(efectivoEgreso),
                new BigDecimal(yapeIngreso), new BigDecimal(yapeEgreso),
                new BigDecimal(plinIngreso), new BigDecimal(plinEgreso),
                esperadoYape, null, null, esperadoPlin, null, null,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, null);
    }

    private CajaTotalesSesion totalesCerrados(String efectivoIngreso, String efectivoEgreso,
                                              String yapeIngreso, String yapeEgreso,
                                              String plinIngreso, String plinEgreso,
                                              String yapeContado, String plinContado) {
        CajaTotalesSesion totales = totales(efectivoIngreso, efectivoEgreso, yapeIngreso, yapeEgreso, plinIngreso, plinEgreso);
        BigDecimal esperadoYape = totales.montoEsperadoYape();
        BigDecimal esperadoPlin = totales.montoEsperadoPlin();
        BigDecimal contadoYape = new BigDecimal(yapeContado);
        BigDecimal contadoPlin = new BigDecimal(plinContado);
        return new CajaTotalesSesion(totales.ingresosEfectivo(), totales.egresosEfectivo(),
                totales.ingresosYape(), totales.egresosYape(), totales.ingresosPlin(), totales.egresosPlin(),
                esperadoYape, contadoYape, contadoYape.subtract(esperadoYape),
                esperadoPlin, contadoPlin, contadoPlin.subtract(esperadoPlin),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Cierre: un odontólogo de otra sede no puede cerrar la caja")
    void cerrarCaja_OdontologoDeOtraSede_Denegado() {
        authenticateUser("ODONTOLOGO", "sede-eltambo");
        when(cajaSesionRepository.findById(sesionId)).thenReturn(Optional.of(sesionAbiertaPorOtro()));

        assertThatThrownBy(() -> cajaService.cerrarCaja(sesionId,
                CajaCierreRequestDTO.builder().montoContado(BigDecimal.TEN).build()))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verify(cajaSesionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Cierre: recepción que no abrió la caja sigue sin poder cerrarla (403)")
    void cerrarCaja_RecepcionQueNoAbrio_Denegado() {
        authenticateUser("RECEPCION", "sede-chilca-1");
        when(cajaSesionRepository.findById(sesionId)).thenReturn(Optional.of(sesionAbiertaPorOtro()));

        assertThatThrownBy(() -> cajaService.cerrarCaja(sesionId,
                CajaCierreRequestDTO.builder().montoContado(BigDecimal.TEN).build()))
                .isInstanceOf(ResponseStatusException.class);
        verify(cajaSesionRepository, never()).save(any());
    }
}
