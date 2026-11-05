package com.odontomax.caja;

import com.odontomax.application.dto.request.PagoAnulacionRequestDTO;
import com.odontomax.application.dto.request.PagoCreateRequestDTO;
import com.odontomax.application.service.PagoService;
import com.odontomax.application.service.CajaService;
import com.odontomax.application.dto.response.PagoResponseDTO;
import com.odontomax.domain.port.out.ComprobanteElectronicoPort;
import com.odontomax.infrastructure.persistence.entity.CajaMovimientoEntity;
import com.odontomax.infrastructure.persistence.entity.CajaSesionEntity;
import com.odontomax.pacientes.infrastructure.persistence.entity.PacienteEntity;
import com.odontomax.pacientes.infrastructure.persistence.repository.SpringDataPacienteRepository;
import com.odontomax.infrastructure.persistence.entity.PagoEntity;
import com.odontomax.infrastructure.persistence.repository.*;
import com.odontomax.identidad.api.ClinicaUserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.odontomax.domain.exception.PagoExcedeSaldoTratamientoException;
import com.odontomax.tratamientos.api.TratamientoCobroModuloApi;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PagoServiceTest {

    @Mock
    private SpringDataPagoRepository pagoRepository;

    @Mock
    private SpringDataPacienteRepository pacienteRepository;

    @Mock
    private SpringDataTratamientoRepository tratamientoRepository;

    @Mock
    private SpringDataCajaSesionRepository cajaSesionRepository;

    @Mock
    private SpringDataCajaMovimientoRepository cajaMovimientoRepository;

    @Mock
    private SpringDataAuditoriaRepository auditoriaRepository;

    @Mock
    private ComprobanteElectronicoPort comprobantePort;

    @Mock
    private CajaService cajaService;

    @Mock
    private com.odontomax.agenda.api.ConsultaEspecialistasModuloApi consultaEspecialistasApi;

    @Mock
    private TratamientoCobroModuloApi tratamientoCobroApi;

    @InjectMocks
    private PagoService pagoService;

    private UUID userId;
    private UUID tratamientoId;
    private ClinicaUserPrincipal principal;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        tratamientoId = UUID.randomUUID();
        principal = new ClinicaUserPrincipal(
                userId,
                "cajero@odontomax.pe",
                "Cajero Recepción",
                "RECEPCION",
                "sede-chilca-1"
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority("ROLE_RECEPCION")))
        );
        pagoService.setTratamientoCobroApi(tratamientoCobroApi);
        lenient().when(cajaSesionRepository.findBySedeIdAndAbiertaPorAndCerradaEnIsNull(anyString(), eq(userId)))
                .thenReturn(Optional.empty());
        lenient().when(cajaSesionRepository.findBySedeIdAndCerradaEnIsNull(anyString()))
                .thenReturn(Optional.empty());
        lenient().when(cajaService.obtenerOAbrirSesionAutomatica(anyString())).thenReturn(
                CajaSesionEntity.builder().id(UUID.randomUUID()).sedeId("sede-chilca-1").abiertaPor(userId).build());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Pago en EFECTIVO sin caja abierta abre una caja automática (S/ 0.00) y se asocia a ella")
    void registrarPago_EfectivoSinCajaAbierta_AbreCajaAutomatica() {
        UUID pacienteId = UUID.randomUUID();
        PacienteEntity paciente = PacienteEntity.builder()
                .id(pacienteId)
                .nombres("Carlos")
                .apellidos("Quispe")
                .sedeId("sede-chilca-1")
                .saldoPendiente(new BigDecimal("100.00"))
                .build();

        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(paciente));
        // Sin sesión de caja abierta
        when(cajaSesionRepository.findBySedeIdAndAbiertaPorAndCerradaEnIsNull("sede-chilca-1", userId))
                .thenReturn(Optional.empty());
        when(cajaSesionRepository.findBySedeIdAndCerradaEnIsNull("sede-chilca-1"))
                .thenReturn(Optional.empty());

        PagoCreateRequestDTO dto = PagoCreateRequestDTO.builder()
                .pacienteId(pacienteId)
                .sedeId("sede-chilca-1")
                .tratamientoId(tratamientoId)
                .monto(new BigDecimal("50.00"))
                .metodoPago("EFECTIVO")
                .build();
        prepararTratamiento(pacienteId, new BigDecimal("100.00"), BigDecimal.ZERO);

        UUID sesionAutoId = UUID.randomUUID();
        CajaSesionEntity sesionAuto = CajaSesionEntity.builder()
                .id(sesionAutoId).sedeId("sede-chilca-1").abiertaPor(userId).montoApertura(BigDecimal.ZERO).build();
        when(cajaService.obtenerOAbrirSesionAutomatica("sede-chilca-1")).thenReturn(sesionAuto);
        when(comprobantePort.emitir(any())).thenReturn(new ComprobanteElectronicoPort.ComprobanteEmitido(
                "RECIBO", "R001", "000102", null, null, null, LocalDateTime.now(), "EMITIDO", "OK"
        ));
        when(pagoRepository.save(any(PagoEntity.class))).thenAnswer(i -> {
            PagoEntity p = i.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        PagoResponseDTO response = pagoService.registrarPago(dto);

        assertEquals(sesionAutoId, response.getCajaSesionId());
        verify(cajaService).obtenerOAbrirSesionAutomatica("sede-chilca-1");
        verify(cajaMovimientoRepository).save(argThat(m ->
                "INGRESO".equals(m.getTipo()) && m.getSesion().getId().equals(sesionAutoId)));
    }

    private PagoCreateRequestDTO pagoYape(UUID pacienteId, UUID especialistaId) {
        return PagoCreateRequestDTO.builder()
                .pacienteId(pacienteId).sedeId("sede-chilca-1").tratamientoId(tratamientoId).monto(new BigDecimal("80.00"))
                .metodoPago("YAPE").especialistaId(especialistaId).build();
    }

    private void prepararTratamiento(UUID pacienteId, BigDecimal costo, BigDecimal pagadoAntes) {
        BigDecimal saldoDisponible = costo.subtract(pagadoAntes);
        when(tratamientoCobroApi.validarSaldoParaCobro(eq(pacienteId), eq(tratamientoId), any()))
                .thenAnswer(invocation -> {
                    BigDecimal monto = invocation.getArgument(2);
                    if (monto.compareTo(saldoDisponible) > 0) {
                        throw new PagoExcedeSaldoTratamientoException(saldoDisponible);
                    }
                    return new TratamientoCobroModuloApi.TratamientoParaCobro("Tratamiento", null);
                });
    }

    private void prepararCobroYape(UUID pacienteId) {
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(PacienteEntity.builder()
                .id(pacienteId).nombres("Ana").apellidos("Soto").sedeId("sede-chilca-1").build()));
        prepararTratamiento(pacienteId, new BigDecimal("1000.00"), BigDecimal.ZERO);
        when(comprobantePort.emitir(any())).thenReturn(new ComprobanteElectronicoPort.ComprobanteEmitido(
                "RECIBO", "R001", "000200", null, null, null, LocalDateTime.now(), "EMITIDO", "OK"));
        when(pagoRepository.save(any(PagoEntity.class))).thenAnswer(i -> {
            PagoEntity p = i.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });
    }

    private void autenticarComo(String rol) {
        principal = new ClinicaUserPrincipal(userId, rol.toLowerCase() + "@odontomax.pe", rol, rol, "sede-chilca-1");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + rol))));
    }

    @Test
    @DisplayName("Cobro de recepción: queda atribuido al doctor elegido")
    void registrarPago_RecepcionElegiDoctor_QuedaAtribuido() {
        UUID pacienteId = UUID.randomUUID();
        UUID doctorA = UUID.randomUUID();
        prepararCobroYape(pacienteId);
        when(consultaEspecialistasApi.existsById(doctorA)).thenReturn(true);

        pagoService.registrarPago(pagoYape(pacienteId, doctorA));

        verify(pagoRepository).save(argThat(p -> doctorA.equals(p.getEspecialistaId())));
    }

    @Test
    @DisplayName("Cobro hecho por un odontólogo: se atribuye a su propio especialista aunque pida otro")
    void registrarPago_Odontologo_SeAtribuyeASiMismo() {
        UUID pacienteId = UUID.randomUUID();
        UUID propio = UUID.randomUUID();
        autenticarComo("ODONTOLOGO");
        prepararCobroYape(pacienteId);
        when(consultaEspecialistasApi.findEspecialistaIdByUsuarioId(userId)).thenReturn(Optional.of(propio));

        pagoService.registrarPago(pagoYape(pacienteId, UUID.randomUUID()));

        verify(pagoRepository).save(argThat(p -> propio.equals(p.getEspecialistaId())));
    }

    @Test
    @DisplayName("Cobro de recepción con un doctor inexistente se rechaza")
    void registrarPago_DoctorInexistente_Lanza() {
        UUID pacienteId = UUID.randomUUID();
        UUID fantasma = UUID.randomUUID();
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(PacienteEntity.builder()
                .id(pacienteId).nombres("Ana").apellidos("Soto").sedeId("sede-chilca-1").build()));
        prepararTratamiento(pacienteId, new BigDecimal("1000.00"), BigDecimal.ZERO);
        when(consultaEspecialistasApi.existsById(fantasma)).thenReturn(false);
        when(comprobantePort.emitir(any())).thenReturn(new ComprobanteElectronicoPort.ComprobanteEmitido(
                "RECIBO", "R001", "000201", null, null, null, LocalDateTime.now(), "EMITIDO", "OK"));

        assertThrows(IllegalArgumentException.class, () -> pagoService.registrarPago(pagoYape(pacienteId, fantasma)));
    }

    @Test
    @DisplayName("Un odontólogo solo ve en el listado de la sede los cobros atribuidos a él")
    void listarPorSede_Odontologo_SoloLosSuyos() {
        UUID propio = UUID.randomUUID();
        autenticarComo("ODONTOLOGO");
        when(consultaEspecialistasApi.findEspecialistaIdByUsuarioId(userId)).thenReturn(Optional.of(propio));
        PagoEntity suyo = PagoEntity.builder().id(UUID.randomUUID()).sedeId("sede-chilca-1").especialistaId(propio)
                .monto(new BigDecimal("50")).build();
        PagoEntity ajeno = PagoEntity.builder().id(UUID.randomUUID()).sedeId("sede-chilca-1").especialistaId(UUID.randomUUID())
                .monto(new BigDecimal("70")).build();
        PagoEntity sinDoctor = PagoEntity.builder().id(UUID.randomUUID()).sedeId("sede-chilca-1")
                .monto(new BigDecimal("20")).build();
        when(pagoRepository.findBySedeIdOrderByPagadoEnDesc("sede-chilca-1")).thenReturn(List.of(suyo, ajeno, sinDoctor));

        List<PagoResponseDTO> lista = pagoService.listarPorSede("sede-chilca-1");

        assertEquals(1, lista.size());
        assertEquals(suyo.getId(), lista.get(0).getId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"YAPE", "PLIN"})
    @DisplayName("Pago digital sin caja abierta abre sesión automática y registra movimiento por método")
    void registrarPago_MetodoDigitalSinCajaAbierta_AbreCajaAutomatica(String metodo) {
        UUID pacienteId = UUID.randomUUID();
        PacienteEntity paciente = PacienteEntity.builder()
                .id(pacienteId).nombres("Ana").apellidos("Ríos").sedeId("sede-chilca-1")
                .saldoPendiente(BigDecimal.ZERO).build();
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(paciente));
        prepararTratamiento(pacienteId, new BigDecimal("100.00"), BigDecimal.ZERO);
        when(cajaSesionRepository.findBySedeIdAndAbiertaPorAndCerradaEnIsNull("sede-chilca-1", userId))
                .thenReturn(Optional.empty());
        when(cajaSesionRepository.findBySedeIdAndCerradaEnIsNull("sede-chilca-1")).thenReturn(Optional.empty());
        when(comprobantePort.emitir(any())).thenReturn(new ComprobanteElectronicoPort.ComprobanteEmitido(
                "RECIBO", "R001", "000103", null, null, null, LocalDateTime.now(), "EMITIDO", "OK"
        ));
        when(pagoRepository.save(any(PagoEntity.class))).thenAnswer(i -> {
            PagoEntity p = i.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        PagoResponseDTO response = pagoService.registrarPago(PagoCreateRequestDTO.builder()
                .pacienteId(pacienteId).sedeId("sede-chilca-1").tratamientoId(tratamientoId)
                .monto(new BigDecimal("30.00")).metodoPago(metodo).build());

        assertNotNull(response.getCajaSesionId());
        verify(cajaService).obtenerOAbrirSesionAutomatica("sede-chilca-1");
        verify(cajaMovimientoRepository).save(argThat(m ->
                "INGRESO".equals(m.getTipo())
                        && metodo.equals(m.getMetodoPago())
                        && m.getSesion().getId().equals(response.getCajaSesionId())));
    }

    @Test
    @DisplayName("Pago en EFECTIVO con caja abierta asocia la sesión del servidor e ignora la del cliente")
    void registrarPago_EfectivoConCajaAbierta_AsociaSesionYRegistraMovimiento() {
        UUID pacienteId = UUID.randomUUID();
        UUID sesionRealId = UUID.randomUUID();
        UUID sesionFalsaCliente = UUID.randomUUID();

        PacienteEntity paciente = PacienteEntity.builder()
                .id(pacienteId)
                .nombres("Carlos")
                .apellidos("Quispe")
                .sedeId("sede-chilca-1")
                .saldoPendiente(new BigDecimal("200.00"))
                .build();

        CajaSesionEntity sesionReal = CajaSesionEntity.builder()
                .id(sesionRealId)
                .sedeId("sede-chilca-1")
                .abiertaPor(userId)
                .build();

        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(paciente));
        prepararTratamiento(pacienteId, new BigDecimal("200.00"), BigDecimal.ZERO);
        when(cajaSesionRepository.findBySedeIdAndAbiertaPorAndCerradaEnIsNull("sede-chilca-1", userId))
                .thenReturn(Optional.of(sesionReal));

        when(comprobantePort.emitir(any())).thenReturn(new ComprobanteElectronicoPort.ComprobanteEmitido(
                "RECIBO", "R001", "000101", null, null, null, LocalDateTime.now(), "EMITIDO", "OK"
        ));

        when(pagoRepository.save(any(PagoEntity.class))).thenAnswer(i -> {
            PagoEntity p = i.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });


        PagoCreateRequestDTO dto = PagoCreateRequestDTO.builder()
                .pacienteId(pacienteId)
                .sedeId("sede-chilca-1")
                .tratamientoId(tratamientoId)
                .monto(new BigDecimal("100.00"))
                .metodoPago("EFECTIVO")
                .cajaSesionId(sesionFalsaCliente) // Se debe ignorar
                .build();

        PagoResponseDTO response = pagoService.registrarPago(dto);

        assertNotNull(response);
        assertEquals(sesionRealId, response.getCajaSesionId(), "Debe usar la sesión real del servidor, no la del cliente");
        verify(cajaMovimientoRepository).save(argThat(m ->
                "INGRESO".equals(m.getTipo()) &&
                m.getMonto().compareTo(new BigDecimal("100.00")) == 0 &&
                m.getSesion().getId().equals(sesionRealId)
        ));
    }

    @Test
    @DisplayName("Anulación de pago en EFECTIVO sin caja abierta actual es rechazada con 409 Conflict")
    void anularPago_EfectivoSinCajaAbiertaActual_Lanza409Conflict() {
        UUID pagoId = UUID.randomUUID();
        PagoEntity pago = PagoEntity.builder()
                .id(pagoId)
                .sedeId("sede-chilca-1")
                .pacienteId(UUID.randomUUID())
                .monto(new BigDecimal("120.00"))
                .metodoPago("EFECTIVO")
                .comprobante("R001-000050")
                .anulado(false)
                .build();

        when(pagoRepository.findById(pagoId)).thenReturn(Optional.of(pago));
        // No hay caja abierta en este momento
        when(cajaSesionRepository.findBySedeIdAndAbiertaPorAndCerradaEnIsNull("sede-chilca-1", userId))
                .thenReturn(Optional.empty());
        when(cajaSesionRepository.findBySedeIdAndCerradaEnIsNull("sede-chilca-1"))
                .thenReturn(Optional.empty());

        PagoAnulacionRequestDTO dto = new PagoAnulacionRequestDTO("Error de digitación");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> pagoService.anularPago(pagoId, dto));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("no existe ninguna caja abierta"));
        verify(cajaMovimientoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Anulación de pago en EFECTIVO con caja abierta actual registra EGRESO compensatorio")
    void anularPago_EfectivoConCajaAbierta_RegistraEgresoCompensatorio() {
        UUID pagoId = UUID.randomUUID();
        UUID sesionActivaId = UUID.randomUUID();

        PagoEntity pago = PagoEntity.builder()
                .id(pagoId)
                .sedeId("sede-chilca-1")
                .pacienteId(UUID.randomUUID())
                .monto(new BigDecimal("120.00"))
                .metodoPago("EFECTIVO")
                .comprobante("R001-000050")
                .comprobanteSerie("R001")
                .comprobanteNumero("000050")
                .anulado(false)
                .build();

        CajaSesionEntity sesionActiva = CajaSesionEntity.builder()
                .id(sesionActivaId)
                .sedeId("sede-chilca-1")
                .build();

        when(pagoRepository.findById(pagoId)).thenReturn(Optional.of(pago));
        when(pagoRepository.save(any(PagoEntity.class))).thenAnswer(i -> i.getArgument(0));
        when(cajaSesionRepository.findBySedeIdAndAbiertaPorAndCerradaEnIsNull("sede-chilca-1", userId))
                .thenReturn(Optional.of(sesionActiva));

        PagoAnulacionRequestDTO dto = new PagoAnulacionRequestDTO("Devolución por cancelación");

        PagoResponseDTO response = pagoService.anularPago(pagoId, dto);

        assertNotNull(response);
        assertTrue(response.getAnulado());
        verify(cajaMovimientoRepository).save(argThat(m ->
                "EGRESO".equals(m.getTipo()) &&
                m.getMonto().compareTo(new BigDecimal("120.00")) == 0 &&
                m.getSesion().getId().equals(sesionActivaId) &&
                m.getConcepto().contains("Anulación de pago (R001-000050)")
        ));
        verify(comprobantePort).anular(eq("R001"), eq("000050"), eq("Devolución por cancelación"));
    }
    @Test
    @DisplayName("listarPorPaciente: odontólogo sin cita con el paciente recibe AccessDeniedException")
    void listarPorPaciente_OdontologoSinCita_Denegado() {
        UUID pacienteId = UUID.randomUUID();
        doThrow(new org.springframework.security.access.AccessDeniedException("Acceso denegado: el paciente no está asignado a este odontólogo."))
                .when(consultaEspecialistasApi).exigirAccesoDeOdontologo(pacienteId);

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> pagoService.listarPorPaciente(pacienteId));
    }

    @Test
    @DisplayName("registrarPago: odontólogo sin cita con el paciente recibe AccessDeniedException")
    void registrarPago_OdontologoSinCita_Denegado() {
        UUID pacienteId = UUID.randomUUID();
        doThrow(new org.springframework.security.access.AccessDeniedException("Acceso denegado: el paciente no está asignado a este odontólogo."))
                .when(consultaEspecialistasApi).exigirAccesoDeOdontologo(pacienteId);

        PagoCreateRequestDTO dto = PagoCreateRequestDTO.builder()
                .pacienteId(pacienteId)
                .sedeId("sede-chilca-1")
                .metodoPago("EFECTIVO")
                .monto(new BigDecimal("50.00"))
                .build();

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> pagoService.registrarPago(dto));
    }

}
