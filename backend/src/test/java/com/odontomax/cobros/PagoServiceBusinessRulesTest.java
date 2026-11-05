package com.odontomax.cobros;

import com.odontomax.agenda.api.ConsultaEspecialistasModuloApi;
import com.odontomax.application.dto.request.PagoCreateRequestDTO;
import com.odontomax.application.service.CajaService;
import com.odontomax.application.service.PagoService;
import com.odontomax.domain.exception.ComprobanteSaldoPendienteException;
import com.odontomax.domain.exception.PagoExcedeSaldoTratamientoException;
import com.odontomax.domain.exception.PagoSinTratamientoException;
import com.odontomax.domain.port.out.ComprobanteElectronicoPort;
import com.odontomax.infrastructure.persistence.repository.SpringDataAuditoriaRepository;
import com.odontomax.infrastructure.persistence.repository.SpringDataCajaMovimientoRepository;
import com.odontomax.infrastructure.persistence.repository.SpringDataCajaSesionRepository;
import com.odontomax.infrastructure.persistence.repository.SpringDataPagoRepository;
import com.odontomax.infrastructure.persistence.repository.SpringDataTratamientoRepository;
import com.odontomax.identidad.api.ClinicaUserPrincipal;
import com.odontomax.pacientes.infrastructure.persistence.entity.PacienteEntity;
import com.odontomax.pacientes.infrastructure.persistence.repository.SpringDataPacienteRepository;
import com.odontomax.tratamientos.api.TratamientoCobroModuloApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PagoServiceBusinessRulesTest {

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
    private ConsultaEspecialistasModuloApi consultaEspecialistasApi;
    @Mock
    private TratamientoCobroModuloApi tratamientoCobroApi;

    @InjectMocks
    private PagoService pagoService;

    private UUID pacienteId;
    private UUID tratamientoId;

    @BeforeEach
    void setUp() {
        UUID usuarioId = UUID.randomUUID();
        pacienteId = UUID.randomUUID();
        tratamientoId = UUID.randomUUID();
        ClinicaUserPrincipal principal = new ClinicaUserPrincipal(
                usuarioId, "recepcion@odontomax.pe", "Recepción", "RECEPCION", "sede-chilca-1");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_RECEPCION"))));
        pagoService.setTratamientoCobroApi(tratamientoCobroApi);
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(PacienteEntity.builder()
                .id(pacienteId).nombres("Ana").apellidos("Soto").sedeId("sede-chilca-1").build()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rechazaPagoSinTratamiento() {
        PagoCreateRequestDTO dto = PagoCreateRequestDTO.builder()
                .pacienteId(pacienteId).sedeId("sede-chilca-1")
                .monto(new BigDecimal("10.00")).metodoPago("EFECTIVO").build();

        PagoSinTratamientoException exception = assertThrows(
                PagoSinTratamientoException.class, () -> pagoService.registrarPago(dto));

        assertEquals("El cobro debe pertenecer a un plan de tratamiento", exception.getMessage());
        verify(comprobantePort, never()).emitir(any());
        verify(pagoRepository, never()).save(any());
    }

    @Test
    void rechazaPagoMayorAlSaldoDelTratamiento() {
        when(tratamientoCobroApi.validarSaldoParaCobro(eq(pacienteId), eq(tratamientoId), any()))
                .thenThrow(new PagoExcedeSaldoTratamientoException(new BigDecimal("40.00")));
        PagoCreateRequestDTO dto = PagoCreateRequestDTO.builder()
                .pacienteId(pacienteId).tratamientoId(tratamientoId).sedeId("sede-chilca-1")
                .monto(new BigDecimal("41.00")).metodoPago("EFECTIVO").build();

        PagoExcedeSaldoTratamientoException exception = assertThrows(
                PagoExcedeSaldoTratamientoException.class, () -> pagoService.registrarPago(dto));

        org.junit.jupiter.api.Assertions.assertTrue(exception.getMessage().contains("S/ 40.00"));
        verify(comprobantePort, never()).emitir(any());
        verify(pagoRepository, never()).save(any());
    }

    @Test
    void rechazaBoletaOFacturaSiTratamientoTieneSaldoPendiente() {
        when(tratamientoCobroApi.validarSaldoParaCobro(eq(pacienteId), eq(tratamientoId), any()))
                .thenReturn(new TratamientoCobroModuloApi.TratamientoParaCobro("Plan Ortodoncia", UUID.randomUUID(), new BigDecimal("1260.00")));
        PagoCreateRequestDTO dto = PagoCreateRequestDTO.builder()
                .pacienteId(pacienteId).tratamientoId(tratamientoId).sedeId("sede-chilca-1")
                .monto(new BigDecimal("240.00")).metodoPago("EFECTIVO")
                .comprobanteTipo("BOLETA")
                .build();

        ComprobanteSaldoPendienteException exception = assertThrows(
                ComprobanteSaldoPendienteException.class, () -> pagoService.registrarPago(dto));

        org.junit.jupiter.api.Assertions.assertEquals(
                "Falta pagar S/ 1260.00 para emitir el comprobante", exception.getMessage());
        org.junit.jupiter.api.Assertions.assertEquals(new BigDecimal("1260.00"), exception.getSaldoPendiente());
        verify(comprobantePort, never()).emitir(any());
        verify(pagoRepository, never()).save(any());
    }
}
