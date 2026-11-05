package com.odontomax.infrastructure.adapter.sunat;

import com.odontomax.domain.port.out.ComprobanteElectronicoPort;
import com.odontomax.infrastructure.persistence.entity.ComprobanteCorrelativoEntity;
import com.odontomax.infrastructure.persistence.repository.SpringDataComprobanteCorrelativoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProveedorSunatSimuladoAdapterTest {

    @Mock
    private SpringDataComprobanteCorrelativoRepository correlativoRepository;

    @InjectMocks
    private ProveedorSunatSimuladoAdapter adapter;

    @BeforeEach
    void setUp() {
    }

    @Test
    @DisplayName("Emisión de RECIBO interno incrementa correlativo persistente por serie")
    void emitirRecibo_SedeChilca1_GeneraR001YCorrelativoSecuencial() {
        ComprobanteCorrelativoEntity entidadExistente = ComprobanteCorrelativoEntity.builder()
                .serie("R001")
                .ultimoNumero(45L)
                .actualizadoEn(Instant.now())
                .build();

        when(correlativoRepository.findBySerieWithLock("R001")).thenReturn(Optional.of(entidadExistente));
        when(correlativoRepository.save(any(ComprobanteCorrelativoEntity.class))).thenAnswer(i -> i.getArgument(0));

        ComprobanteElectronicoPort.SolicitudEmision solicitud = new ComprobanteElectronicoPort.SolicitudEmision(
                "RECIBO",
                "sede-chilca-1",
                "Juan Pérez",
                "12345678",
                "DNI",
                new BigDecimal("150.00"),
                "EFECTIVO",
                "Curación dental",
                "ref-123"
        );

        ComprobanteElectronicoPort.ComprobanteEmitido emitido = adapter.emitir(solicitud);

        assertNotNull(emitido);
        assertEquals("RECIBO", emitido.tipoComprobante());
        assertEquals("R001", emitido.serie());
        assertEquals("000046", emitido.numero());
        assertNull(emitido.codigoHash(), "No debe incluir hash simulado falso");
        assertNull(emitido.qrData(), "No debe incluir QR con RUC ficticio");
        assertNull(emitido.pdfUrlSimulada(), "No debe incluir URL de PDF falso");
        assertEquals("EMITIDO", emitido.estadoSunat());

        verify(correlativoRepository).save(argThat(c -> c.getUltimoNumero() == 46L));
    }

    @Test
    @DisplayName("Cualquier otra sede comparte la serie R001 de recibos")
    void emitirRecibo_OtraSede_CompartaSerieR001() {
        ComprobanteCorrelativoEntity entidadExistente = ComprobanteCorrelativoEntity.builder()
                .serie("R001")
                .ultimoNumero(0L)
                .actualizadoEn(Instant.now())
                .build();

        when(correlativoRepository.findBySerieWithLock("R001")).thenReturn(Optional.of(entidadExistente));
        when(correlativoRepository.save(any(ComprobanteCorrelativoEntity.class))).thenAnswer(i -> i.getArgument(0));

        ComprobanteElectronicoPort.SolicitudEmision solicitud = new ComprobanteElectronicoPort.SolicitudEmision(
                "RECIBO",
                "sede-eltambo",
                "Maria Lopez",
                "87654321",
                "DNI",
                new BigDecimal("80.00"),
                "YAPE",
                "Limpieza",
                null
        );

        ComprobanteElectronicoPort.ComprobanteEmitido emitido = adapter.emitir(solicitud);

        assertEquals("R001", emitido.serie());
        assertEquals("000001", emitido.numero());
    }

    @Test
    @DisplayName("Rechaza emisión de BOLETA electrónica si el tratamiento tiene saldo pendiente (HTTP 409)")
    void emitir_BoletaConSaldoPendiente_LanzaComprobanteSaldoPendienteException() {
        ComprobanteElectronicoPort.SolicitudEmision solicitud = new ComprobanteElectronicoPort.SolicitudEmision(
                "BOLETA",
                "sede-chilca-1",
                "Pedro Gomez",
                "44556677",
                "DNI",
                new BigDecimal("200.00"),
                "EFECTIVO",
                "Plan Ortodoncia",
                "tratamiento-123",
                new BigDecimal("1300.00")
        );

        com.odontomax.domain.exception.ComprobanteSaldoPendienteException ex =
                assertThrows(com.odontomax.domain.exception.ComprobanteSaldoPendienteException.class, () -> adapter.emitir(solicitud));
        assertTrue(ex.getMessage().contains("Falta pagar S/ 1300.00 para emitir el comprobante"));
        assertEquals(new BigDecimal("1300.00"), ex.getSaldoPendiente());
        verifyNoInteractions(correlativoRepository);
    }

    @Test
    @DisplayName("Rechaza emisión de BOLETA electrónica con HTTP 422")
    void emitir_Boleta_Lanza422UnprocessableEntity() {
        ComprobanteElectronicoPort.SolicitudEmision solicitud = new ComprobanteElectronicoPort.SolicitudEmision(
                "BOLETA",
                "sede-chilca-1",
                "Pedro Gomez",
                "44556677",
                "DNI",
                new BigDecimal("200.00"),
                "EFECTIVO",
                "Extracción",
                null
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> adapter.emitir(solicitud));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatusCode());
        assertTrue(ex.getReason().contains("BOLETA"));
        verifyNoInteractions(correlativoRepository);
    }

    @Test
    @DisplayName("Rechaza emisión de FACTURA electrónica con HTTP 422")
    void emitir_Factura_Lanza422UnprocessableEntity() {
        ComprobanteElectronicoPort.SolicitudEmision solicitud = new ComprobanteElectronicoPort.SolicitudEmision(
                "FACTURA",
                "sede-chilca-1",
                "Empresa SAC",
                "20123456789",
                "RUC",
                new BigDecimal("500.00"),
                "EFECTIVO",
                "Atención corporativa",
                null
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> adapter.emitir(solicitud));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatusCode());
        assertTrue(ex.getReason().contains("FACTURA"));
        verifyNoInteractions(correlativoRepository);
    }
}
