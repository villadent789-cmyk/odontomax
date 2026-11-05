package com.odontomax.tratamientos.service;

import com.odontomax.application.dto.request.CuotasPlanRequestDTO;
import com.odontomax.application.dto.request.TratamientoCreateRequestDTO;
import com.odontomax.application.dto.request.TratamientoItemRequestDTO;
import com.odontomax.application.dto.response.TratamientoResponseDTO;
import com.odontomax.infrastructure.persistence.entity.PlanCuotaEntity;
import com.odontomax.infrastructure.persistence.entity.TratamientoEntity;
import com.odontomax.infrastructure.persistence.repository.SpringDataAuditoriaRepository;
import com.odontomax.pacientes.api.ConsultaPacientesModuloApi;
import com.odontomax.pacientes.api.PacienteResumen;
import com.odontomax.infrastructure.persistence.repository.SpringDataPagoRepository;
import com.odontomax.infrastructure.persistence.repository.SpringDataPlanCuotaRepository;
import com.odontomax.infrastructure.persistence.repository.SpringDataTratamientoItemRepository;
import com.odontomax.infrastructure.persistence.repository.SpringDataTratamientoRepository;
import com.odontomax.agenda.api.ConsultaEspecialistasModuloApi;
import com.odontomax.domain.exception.PagoExcedeSaldoTratamientoException;
import com.odontomax.identidad.api.ClinicaUserPrincipal;
import com.odontomax.tratamientos.api.TratamientoCobroModuloApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TratamientoServiceTest {

    @Mock
    private SpringDataTratamientoRepository tratamientoRepository;

    @Mock
    private SpringDataTratamientoItemRepository itemRepository;

    @Mock
    private SpringDataPlanCuotaRepository cuotaRepository;

    @Mock
    private com.odontomax.infrastructure.persistence.repository.SpringDataPlanSesionCambioRepository sesionCambioRepository;

    @Mock
    private ConsultaPacientesModuloApi pacientesApi;

    @Mock
    private SpringDataPagoRepository pagoRepository;

    @Mock
    private ConsultaEspecialistasModuloApi consultaEspecialistasApi;

    @Mock
    private SpringDataAuditoriaRepository auditoriaRepository;

    @InjectMocks
    private TratamientoService tratamientoService;

    private UUID userId;
    private UUID pacienteId;
    private PacienteResumen paciente;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        pacienteId = UUID.randomUUID();
        paciente = new PacienteResumen(pacienteId, null, "Ana", "Torres", null, "sede-chilca-1", null, null, null, 0);

        lenient().when(pacientesApi.findById(pacienteId)).thenReturn(Optional.of(paciente));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(String rol) {
        ClinicaUserPrincipal principal = new ClinicaUserPrincipal(
                userId,
                "test@odontomax.pe",
                "Usuario Test",
                rol,
                "sede-chilca-1"
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + rol)))
        );
    }

    @Test
    @DisplayName("Odontólogo puede autorizar descuento de hasta 10% y el servidor calcula el costo total")
    void crearTratamiento_OdontologoDescuento10Porciento_Permitido() {
        authenticate("ODONTOLOGO");

        when(tratamientoRepository.save(any(TratamientoEntity.class))).thenAnswer(i -> {
            TratamientoEntity t = i.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });

        // Subtotal = 1000, Descuento = 100 (10%)
        TratamientoCreateRequestDTO dto = TratamientoCreateRequestDTO.builder()
                .pacienteId(pacienteId)
                .sedeId("sede-chilca-1")
                .nombre("Rehabilitación Oral")
                .costoTotal(new BigDecimal("1000.00")) // cliente envía 1000
                .descuentoTotal(new BigDecimal("100.00"))
                .items(List.of(
                        TratamientoItemRequestDTO.builder()
                                .nombreProcedimiento("Corona E-max")
                                .precioBase(new BigDecimal("500.00"))
                                .cantidad(2)
                                .descuento(new BigDecimal("100.00"))
                                .build()
                ))
                .build();

        TratamientoResponseDTO res = tratamientoService.crear(dto);

        assertNotNull(res);
        assertEquals(new BigDecimal("900.00"), res.getCostoTotal(), "El servidor debe calcular 1000 - 100 = 900");
        assertEquals(new BigDecimal("100.00"), res.getDescuentoTotal());
        assertEquals(userId, res.getDescuentoAutorizadoPor());
        // Sin etapa de presupuesto: el tratamiento nace en curso
        assertEquals("EN_PROCESO", res.getEstado());
        assertEquals(Boolean.TRUE, res.getAceptadoPorPaciente());
    }

    @Test
    @DisplayName("Plan en cuotas: el costo es la suma del cronograma y se guardan sus cuotas")
    void crearTratamiento_enCuotas_guardaElCronograma() {
        authenticate("ODONTOLOGO");
        when(tratamientoRepository.save(any(TratamientoEntity.class))).thenAnswer(i -> {
            TratamientoEntity t = i.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });

        TratamientoCreateRequestDTO dto = TratamientoCreateRequestDTO.builder()
                .pacienteId(pacienteId)
                .sedeId("sede-chilca-1")
                .nombre("Ortodoncia convencional")
                .costoTotal(new BigDecimal("1.00")) // el cliente no manda el total real: manda el servidor
                .cuotas(CuotasPlanRequestDTO.builder()
                        .montoInicial(new BigDecimal("600"))
                        .montoMensual(new BigDecimal("120"))
                        .cantidadMensuales(3)
                        .primerVencimiento(java.time.LocalDate.of(2026, 11, 1))
                        .build())
                .build();

        TratamientoResponseDTO res = tratamientoService.crear(dto);

        assertEquals(new BigDecimal("960"), res.getCostoTotal());
        assertEquals("CUOTAS", res.getModalidadPago());
        org.mockito.Mockito.verify(cuotaRepository, org.mockito.Mockito.times(4)).save(any(PlanCuotaEntity.class));
    }

    @Test
    @DisplayName("Un plan en cuotas no admite descuento aparte")
    void crearTratamiento_enCuotasConDescuento_seRechaza() {
        authenticate("ODONTOLOGO");
        TratamientoCreateRequestDTO dto = TratamientoCreateRequestDTO.builder()
                .pacienteId(pacienteId)
                .sedeId("sede-chilca-1")
                .nombre("Ortodoncia convencional")
                .costoTotal(new BigDecimal("960"))
                .descuentoTotal(new BigDecimal("50"))
                .cuotas(CuotasPlanRequestDTO.builder()
                        .montoInicial(new BigDecimal("600"))
                        .montoMensual(new BigDecimal("120"))
                        .cantidadMensuales(3)
                        .primerVencimiento(java.time.LocalDate.of(2026, 11, 1))
                        .build())
                .build();

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> tratamientoService.crear(dto));
    }

    @Test
    @DisplayName("Precio acordado distinto al catálogo: exige motivo y deja constancia de quién lo cambió")
    void crearTratamiento_precioAcordado_exigeMotivoYQuedaRegistrado() {
        authenticate("ODONTOLOGO");
        when(tratamientoRepository.save(any(TratamientoEntity.class))).thenAnswer(i -> {
            TratamientoEntity t = i.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });
        TratamientoCreateRequestDTO.TratamientoCreateRequestDTOBuilder base = TratamientoCreateRequestDTO.builder()
                .pacienteId(pacienteId)
                .sedeId("sede-chilca-1")
                .nombre("Beautifil compleja")
                .costoTotal(new BigDecimal("110.00"))
                .precioCatalogo(new BigDecimal("90.00"));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> tratamientoService.crear(base.build()));

        TratamientoResponseDTO res = tratamientoService.crear(base.motivoAjustePrecio("Pieza con mucha pérdida de estructura").build());

        assertEquals(new BigDecimal("110.00"), res.getCostoTotal());
        assertEquals(new BigDecimal("90.00"), res.getPrecioCatalogo());
        assertEquals("Pieza con mucha pérdida de estructura", res.getMotivoAjustePrecio());
    }

    @Test
    @DisplayName("Recepción no puede acordar un precio distinto al del catálogo")
    void crearTratamiento_precioAcordado_recepcionNoPuede() {
        authenticate("RECEPCION");
        TratamientoCreateRequestDTO dto = TratamientoCreateRequestDTO.builder()
                .pacienteId(pacienteId)
                .sedeId("sede-chilca-1")
                .nombre("Beautifil compleja")
                .costoTotal(new BigDecimal("110.00"))
                .precioCatalogo(new BigDecimal("90.00"))
                .motivoAjustePrecio("Pieza con mucha pérdida de estructura")
                .build();

        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> tratamientoService.crear(dto));
    }

    @Test
    @DisplayName("Cuotas vencidas: una fila por plan con la cuota más antigua sin pagar; lo pagado se descuenta en orden")
    void cuotasVencidasDeSede_unaFilaPorPlan() {
        UUID planId = UUID.randomUUID();
        TratamientoEntity plan = TratamientoEntity.builder()
                .id(planId).pacienteId(pacienteId).sedeId("sede-chilca-1").nombre("Ortodoncia convencional")
                .costoTotal(new BigDecimal("840")).pagadoAntesSistema(BigDecimal.ZERO).estado("EN_PROCESO").build();
        when(tratamientoRepository.findBySedeIdAndEstado("sede-chilca-1", "EN_PROCESO")).thenReturn(List.of(plan));
        when(cuotaRepository.findByTratamientoIdOrderByNumeroAsc(planId)).thenReturn(List.of(
                PlanCuotaEntity.builder().tratamientoId(planId).numero(0).concepto("Cuota inicial").venceEn(java.time.LocalDate.of(2026, 9, 1)).monto(new BigDecimal("600")).build(),
                PlanCuotaEntity.builder().tratamientoId(planId).numero(1).concepto("Cuota 1").venceEn(java.time.LocalDate.of(2026, 10, 1)).monto(new BigDecimal("120")).build(),
                PlanCuotaEntity.builder().tratamientoId(planId).numero(2).concepto("Cuota 2").venceEn(java.time.LocalDate.of(2026, 11, 1)).monto(new BigDecimal("120")).build()));
        when(pagoRepository.sumMontoPagadoByTratamientoId(planId)).thenReturn(new BigDecimal("600"));

        var vencidas = tratamientoService.cuotasVencidasDeSede("sede-chilca-1", java.time.LocalDate.of(2026, 10, 15));

        assertEquals(1, vencidas.size());
        assertEquals("Cuota 1", vencidas.get(0).concepto());
        assertEquals(new BigDecimal("120"), vencidas.get(0).saldo());
        assertEquals("Ana Torres", vencidas.get(0).pacienteNombre());
    }

    @Test
    @DisplayName("Odontólogo con descuento superior a 10% es rechazado con 403 Forbidden")
    void crearTratamiento_OdontologoDescuentoExcesivo_Lanza403Forbidden() {
        authenticate("ODONTOLOGO");

        // Subtotal = 1000, Descuento = 150 (15% > 10%)
        TratamientoCreateRequestDTO dto = TratamientoCreateRequestDTO.builder()
                .pacienteId(pacienteId)
                .sedeId("sede-chilca-1")
                .nombre("Ortodoncia Avanzada")
                .costoTotal(new BigDecimal("1000.00"))
                .descuentoTotal(new BigDecimal("150.00"))
                .items(List.of(
                        TratamientoItemRequestDTO.builder()
                                .nombreProcedimiento("Brackets Autoligados")
                                .precioBase(new BigDecimal("1000.00"))
                                .cantidad(1)
                                .descuento(new BigDecimal("150.00"))
                                .build()
                ))
                .build();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> tratamientoService.crear(dto));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertTrue(ex.getReason().contains("límite máximo permitido para su rol de ODONTOLOGO (10.0%)"));
    }

    @Test
    @DisplayName("Administrador de Sede puede autorizar hasta 30% de descuento")
    void crearTratamiento_AdminSedeDescuento25Porciento_Permitido() {
        authenticate("ADMIN_SEDE");

        when(tratamientoRepository.save(any(TratamientoEntity.class))).thenAnswer(i -> {
            TratamientoEntity t = i.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });

        // Subtotal = 1000, Descuento = 250 (25% <= 30%)
        TratamientoCreateRequestDTO dto = TratamientoCreateRequestDTO.builder()
                .pacienteId(pacienteId)
                .sedeId("sede-chilca-1")
                .nombre("Implantes")
                .costoTotal(new BigDecimal("1000.00"))
                .descuentoTotal(new BigDecimal("250.00"))
                .build();

        TratamientoResponseDTO res = tratamientoService.crear(dto);
        assertNotNull(res);
        assertEquals(new BigDecimal("750.00"), res.getCostoTotal());
        assertEquals(new BigDecimal("250.00"), res.getDescuentoTotal());
    }

    @Test
    @DisplayName("Administrador de Sede con descuento superior a 30% es rechazado con 403")
    void crearTratamiento_AdminSedeDescuento35Porciento_Lanza403Forbidden() {
        authenticate("ADMIN_SEDE");

        // Subtotal = 1000, Descuento = 350 (35% > 30%)
        TratamientoCreateRequestDTO dto = TratamientoCreateRequestDTO.builder()
                .pacienteId(pacienteId)
                .sedeId("sede-chilca-1")
                .nombre("Implantes")
                .costoTotal(new BigDecimal("1000.00"))
                .descuentoTotal(new BigDecimal("350.00"))
                .build();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> tratamientoService.crear(dto));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertTrue(ex.getReason().contains("límite máximo permitido para Administrador de Sede (30.0%)"));
    }

    @Test
    @DisplayName("Administrador General puede autorizar más de 30% de descuento")
    void crearTratamiento_AdminGeneralDescuento50Porciento_Permitido() {
        authenticate("ADMIN_GENERAL");

        when(tratamientoRepository.save(any(TratamientoEntity.class))).thenAnswer(i -> {
            TratamientoEntity t = i.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });

        // Subtotal = 1000, Descuento = 500 (50%)
        TratamientoCreateRequestDTO dto = TratamientoCreateRequestDTO.builder()
                .pacienteId(pacienteId)
                .sedeId("sede-chilca-1")
                .nombre("Campaña Social")
                .costoTotal(new BigDecimal("1000.00"))
                .descuentoTotal(new BigDecimal("500.00"))
                .build();

        TratamientoResponseDTO res = tratamientoService.crear(dto);
        assertNotNull(res);
        assertEquals(new BigDecimal("500.00"), res.getCostoTotal());
    }
    @Test
    @DisplayName("Listar: odontólogo sin cita con el paciente recibe AccessDeniedException")
    void testListarPorPaciente_OdontologoSinCita_Denegado() {
        authenticate("ODONTOLOGO");
        doThrow(new org.springframework.security.access.AccessDeniedException("Acceso denegado: el paciente no está asignado a este odontólogo."))
                .when(consultaEspecialistasApi).exigirAccesoDeOdontologo(pacienteId);

        assertThrows(org.springframework.security.access.AccessDeniedException.class, () ->
                tratamientoService.listarPorPaciente(pacienteId));
    }

    @Test
    @DisplayName("Obtener por id: odontólogo sin cita con el paciente recibe AccessDeniedException")
    void testObtenerPorId_OdontologoSinCita_Denegado() {
        authenticate("ODONTOLOGO");
        UUID tratId = UUID.randomUUID();
        TratamientoEntity entity = TratamientoEntity.builder().id(tratId).pacienteId(pacienteId).sedeId("sede-chilca-1").build();
        when(tratamientoRepository.findById(tratId)).thenReturn(Optional.of(entity));
        doThrow(new org.springframework.security.access.AccessDeniedException("Acceso denegado: el paciente no está asignado a este odontólogo."))
                .when(consultaEspecialistasApi).exigirAccesoDeOdontologo(pacienteId);

        assertThrows(org.springframework.security.access.AccessDeniedException.class, () ->
                tratamientoService.obtenerPorId(tratId));
    }
    @Test
    @DisplayName("Listar por sede: odontólogo solo ve tratamientos de pacientes con los que tiene cita")
    void testListarPorSede_Odontologo_FiltraPacientesSinCita() {
        authenticate("ODONTOLOGO");
        UUID pacienteAjenoId = UUID.randomUUID();

        TratamientoEntity tPropio = TratamientoEntity.builder()
                .id(UUID.randomUUID())
                .pacienteId(pacienteId)
                .sedeId("sede-chilca-1")
                .nombre("Tratamiento Asignado")
                .costoTotal(BigDecimal.TEN)
                .descuentoTotal(BigDecimal.ZERO)
                .build();

        TratamientoEntity tAjeno = TratamientoEntity.builder()
                .id(UUID.randomUUID())
                .pacienteId(pacienteAjenoId)
                .sedeId("sede-chilca-1")
                .nombre("Tratamiento Ajeno")
                .costoTotal(BigDecimal.TEN)
                .descuentoTotal(BigDecimal.ZERO)
                .build();

        when(tratamientoRepository.findBySedeIdOrderByCreadoEnDesc("sede-chilca-1"))
                .thenReturn(List.of(tPropio, tAjeno));

        when(consultaEspecialistasApi.pacientesDelOdontologo(userId)).thenReturn(Set.of(pacienteId));

        List<TratamientoResponseDTO> resultado = tratamientoService.listarPorSede("sede-chilca-1");

        assertEquals(1, resultado.size());
        assertEquals(tPropio.getId(), resultado.get(0).getId());
        verify(consultaEspecialistasApi, times(1)).pacientesDelOdontologo(userId);
        verify(consultaEspecialistasApi, never()).odontologoAtiendeAlPaciente(any(), any());
    }

    @Test
    @DisplayName("Cobro superior al saldo disponible del plan es rechazado")
    void validarSaldoParaCobro_MontoMayorAlSaldo_Rechazado() {
        UUID tratamientoId = UUID.randomUUID();
        TratamientoEntity entity = TratamientoEntity.builder()
                .id(tratamientoId)
                .pacienteId(pacienteId)
                .nombre("Ortodoncia")
                .costoTotal(new BigDecimal("100.00"))
                .pagadoAntesSistema(new BigDecimal("20.00"))
                .build();
        when(tratamientoRepository.findByIdForUpdate(tratamientoId)).thenReturn(Optional.of(entity));
        when(pagoRepository.sumMontoPagadoByTratamientoId(tratamientoId)).thenReturn(new BigDecimal("40.00"));

        PagoExcedeSaldoTratamientoException exception = assertThrows(PagoExcedeSaldoTratamientoException.class,
                () -> tratamientoService.validarSaldoParaCobro(pacienteId, tratamientoId, new BigDecimal("41.00")));

        assertTrue(exception.getMessage().contains("S/ 40.00"));
    }

    @Test
    @DisplayName("Cobro válido devuelve el nombre y el especialista del plan")
    void validarSaldoParaCobro_MontoValido_RetornaDatosDelPlan() {
        UUID tratamientoId = UUID.randomUUID();
        UUID especialistaId = UUID.randomUUID();
        TratamientoEntity entity = TratamientoEntity.builder()
                .id(tratamientoId)
                .pacienteId(pacienteId)
                .nombre("Ortodoncia")
                .especialistaId(especialistaId)
                .costoTotal(new BigDecimal("100.00"))
                .pagadoAntesSistema(new BigDecimal("20.00"))
                .build();
        when(tratamientoRepository.findByIdForUpdate(tratamientoId)).thenReturn(Optional.of(entity));
        when(pagoRepository.sumMontoPagadoByTratamientoId(tratamientoId)).thenReturn(new BigDecimal("40.00"));

        TratamientoCobroModuloApi.TratamientoParaCobro resultado = tratamientoService.validarSaldoParaCobro(
                pacienteId, tratamientoId, new BigDecimal("40.00"));

        assertEquals("Ortodoncia", resultado.nombre());
        assertEquals(especialistaId, resultado.especialistaId());
        assertEquals(0, resultado.saldoRestante().compareTo(BigDecimal.ZERO));
    }

    private TratamientoEntity planEnProceso(UUID id, int total, int realizadas) {
        return TratamientoEntity.builder()
                .id(id).pacienteId(pacienteId).sedeId("sede-chilca-1").nombre("Endodoncia")
                .estado("EN_PROCESO").costoTotal(new BigDecimal("400.00"))
                .totalSesiones(total).sesionesRealizadas(realizadas).build();
    }

    @Test
    @DisplayName("Cambiar las sesiones del plan exige un motivo y lo deja registrado")
    void cambiarSesiones_conMotivo_loRegistra() {
        authenticate("ADMIN_GENERAL");
        UUID id = UUID.randomUUID();
        TratamientoEntity entity = planEnProceso(id, 4, 1);
        when(tratamientoRepository.findById(id)).thenReturn(Optional.of(entity));
        when(tratamientoRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        TratamientoResponseDTO r = tratamientoService.actualizar(id,
                com.odontomax.application.dto.request.TratamientoUpdateRequestDTO.builder()
                        .totalSesiones(1).motivoCambioSesiones("Tenía otro paciente agendado").build());

        assertEquals(1, r.getTotalSesiones());
        verify(sesionCambioRepository).save(org.mockito.ArgumentMatchers.argThat(c ->
                c.getSesionesAntes() == 4 && c.getSesionesDespues() == 1 && c.getMotivo().contains("otro paciente")));
    }

    @Test
    @DisplayName("Cambiar las sesiones sin motivo se rechaza")
    void cambiarSesiones_sinMotivo_seRechaza() {
        authenticate("ADMIN_GENERAL");
        UUID id = UUID.randomUUID();
        when(tratamientoRepository.findById(id)).thenReturn(Optional.of(planEnProceso(id, 4, 1)));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> tratamientoService.actualizar(id,
                com.odontomax.application.dto.request.TratamientoUpdateRequestDTO.builder().totalSesiones(2).build()));
        assertTrue(ex.getMessage().contains("motivo del cambio de sesiones"));
        verify(sesionCambioRepository, never()).save(any());
    }

    @Test
    @DisplayName("El plan no puede quedar con menos sesiones que las ya realizadas")
    void cambiarSesiones_pordebajoDeLasRealizadas_seRechaza() {
        authenticate("ADMIN_GENERAL");
        UUID id = UUID.randomUUID();
        when(tratamientoRepository.findById(id)).thenReturn(Optional.of(planEnProceso(id, 4, 3)));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> tratamientoService.actualizar(id,
                com.odontomax.application.dto.request.TratamientoUpdateRequestDTO.builder()
                        .totalSesiones(2).motivoCambioSesiones("Terminamos antes").build()));
        assertTrue(ex.getMessage().contains("ya realizadas (3)"));
    }

    @Test
    @DisplayName("Cerrar plan como TERMINADO actualiza estado, motivo por defecto y cerradoEn")
    void cerrarPlan_Terminado_ActualizaEstado() {
        authenticate("ADMIN_GENERAL");
        UUID tratamientoId = UUID.randomUUID();
        TratamientoEntity entity = TratamientoEntity.builder()
                .id(tratamientoId)
                .pacienteId(pacienteId)
                .sedeId("sede-chilca-1")
                .nombre("Curación simple")
                .estado("EN_PROCESO")
                .costoTotal(new BigDecimal("100.00"))
                .build();
        when(tratamientoRepository.findById(tratamientoId)).thenReturn(Optional.of(entity));
        when(tratamientoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        com.odontomax.application.dto.request.TratamientoUpdateRequestDTO dto =
                com.odontomax.application.dto.request.TratamientoUpdateRequestDTO.builder()
                        .estado("TERMINADO")
                        .build();

        TratamientoResponseDTO resultado = tratamientoService.actualizar(tratamientoId, dto);

        assertEquals("TERMINADO", resultado.getEstado());
        assertEquals("Tratamiento concluido con éxito", resultado.getMotivoCierre());
        assertNotNull(resultado.getCerradoEn());
        verify(auditoriaRepository).save(any());
    }

    @Test
    @DisplayName("Cerrar plan como INTERRUMPIDO sin motivo es rechazado con IllegalArgumentException")
    void cerrarPlan_InterrumpidoSinMotivo_LanzaExcepcion() {
        authenticate("ADMIN_GENERAL");
        UUID tratamientoId = UUID.randomUUID();
        TratamientoEntity entity = TratamientoEntity.builder()
                .id(tratamientoId)
                .pacienteId(pacienteId)
                .sedeId("sede-chilca-1")
                .nombre("Ortodoncia")
                .estado("EN_PROCESO")
                .costoTotal(new BigDecimal("1500.00"))
                .build();
        when(tratamientoRepository.findById(tratamientoId)).thenReturn(Optional.of(entity));

        com.odontomax.application.dto.request.TratamientoUpdateRequestDTO dto =
                com.odontomax.application.dto.request.TratamientoUpdateRequestDTO.builder()
                        .estado("INTERRUMPIDO")
                        .motivoCierre("   ")
                        .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tratamientoService.actualizar(tratamientoId, dto));
        assertTrue(ex.getMessage().contains("Debe ingresar un motivo para interrumpir el tratamiento"));
    }

    @Test
    @DisplayName("Cerrar plan como INTERRUMPIDO con motivo registra el motivo y la fecha")
    void cerrarPlan_InterrumpidoConMotivo_RegistraMotivo() {
        authenticate("ADMIN_GENERAL");
        UUID tratamientoId = UUID.randomUUID();
        TratamientoEntity entity = TratamientoEntity.builder()
                .id(tratamientoId)
                .pacienteId(pacienteId)
                .sedeId("sede-chilca-1")
                .nombre("Ortodoncia")
                .estado("EN_PROCESO")
                .costoTotal(new BigDecimal("1500.00"))
                .build();
        when(tratamientoRepository.findById(tratamientoId)).thenReturn(Optional.of(entity));
        when(tratamientoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        com.odontomax.application.dto.request.TratamientoUpdateRequestDTO dto =
                com.odontomax.application.dto.request.TratamientoUpdateRequestDTO.builder()
                        .estado("INTERRUMPIDO")
                        .motivoCierre("Paciente se mudó de ciudad a Lima")
                        .build();

        TratamientoResponseDTO resultado = tratamientoService.actualizar(tratamientoId, dto);

        assertEquals("INTERRUMPIDO", resultado.getEstado());
        assertEquals("Paciente se mudó de ciudad a Lima", resultado.getMotivoCierre());
        assertNotNull(resultado.getCerradoEn());
    }

    @Test
    @DisplayName("Odontólogo no responsable no puede cerrar o modificar plan ajeno")
    void cerrarPlan_OdontologoNoResponsable_LanzaAccessDenied() {
        UUID tratamientoId = UUID.randomUUID();
        UUID especialistaResponsable = UUID.randomUUID();
        UUID especialistaPropio = UUID.randomUUID();

        // Login as ODONTOLOGO
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new ClinicaUserPrincipal(userId, "dr@odontomax.pe", "Dr. Propio", "ODONTOLOGO", "sede-chilca-1"),
                null, List.of(new SimpleGrantedAuthority("ROLE_ODONTOLOGO"))));

        TratamientoEntity entity = TratamientoEntity.builder()
                .id(tratamientoId)
                .pacienteId(pacienteId)
                .sedeId("sede-chilca-1")
                .especialistaId(especialistaResponsable)
                .nombre("Endodoncia")
                .estado("EN_PROCESO")
                .costoTotal(new BigDecimal("400.00"))
                .build();
        when(tratamientoRepository.findById(tratamientoId)).thenReturn(Optional.of(entity));
        when(consultaEspecialistasApi.findEspecialistaIdByUsuarioId(userId)).thenReturn(Optional.of(especialistaPropio));

        com.odontomax.application.dto.request.TratamientoUpdateRequestDTO dto =
                com.odontomax.application.dto.request.TratamientoUpdateRequestDTO.builder()
                        .estado("TERMINADO")
                        .build();

        org.springframework.security.access.AccessDeniedException ex = assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> tratamientoService.actualizar(tratamientoId, dto));
        assertTrue(ex.getMessage().contains("Solo el odontólogo responsable del plan o la administración"));
    }
}
