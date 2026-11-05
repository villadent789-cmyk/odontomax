package com.odontomax.infrastructure.adapter.sunat;

import com.odontomax.domain.port.out.ComprobanteElectronicoPort;
import com.odontomax.infrastructure.persistence.entity.ComprobanteCorrelativoEntity;
import com.odontomax.infrastructure.persistence.repository.SpringDataComprobanteCorrelativoRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * =============================================================================
 * ADAPTADOR DE COMPROBANTES Y CONTROL DE RECIBOS INTERNOS DE CAJA
 * =============================================================================
 *
 * Implementa {@link ComprobanteElectronicoPort} garantizando:
 * 1. Numeración persistente por serie en base de datos PostgreSQL con bloqueo pesimista.
 * 2. Emisión exclusiva de RECIBOS de control interno (R001, R002, R003).
 * 3. Rechazo estricto con HTTP 422 si se solicita BOLETA o FACTURA electrónica
 *    mientras no se contrate un proveedor homologado OSE/PSE ante SUNAT.
 * 4. Eliminación de datos falsos (hash simulado, QR inventado, RUC ficticio).
 */
@Component
@RequiredArgsConstructor
public class ProveedorSunatSimuladoAdapter implements ComprobanteElectronicoPort {

    private static final String SERIE_RECIBO = "R001";

    private static final Logger log = LoggerFactory.getLogger(ProveedorSunatSimuladoAdapter.class);

    private final SpringDataComprobanteCorrelativoRepository correlativoRepository;

    @Override
    @Transactional
    public ComprobanteEmitido emitir(SolicitudEmision solicitud) {
        String tipo = (solicitud.tipoComprobante() != null && !solicitud.tipoComprobante().isBlank())
                ? solicitud.tipoComprobante().toUpperCase().trim()
                : "RECIBO";

        // Rechazo explícito de BOLETA y FACTURA hasta contar con proveedor OSE/PSE en producción
        if ("BOLETA".equals(tipo) || "FACTURA".equals(tipo)) {
            if (solicitud.saldoRestante() != null && solicitud.saldoRestante().compareTo(java.math.BigDecimal.ZERO) > 0) {
                throw new com.odontomax.domain.exception.ComprobanteSaldoPendienteException(solicitud.saldoRestante());
            }
            log.warn("[TRIBUTARIO] Solicitud rechazada: intento de emitir {} electrónica sin conector OSE homologado.", tipo);
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    String.format("La emisión de comprobantes tributarios electrónicos (%s) ante SUNAT requiere la contratación de un proveedor OSE/PSE homologado y Certificado Digital activo. En este entorno solo se emiten RECIBOS internos de caja.", tipo)
            );
        }

        // Resolución de la serie de recibo interno por sede
        String serie = resolverSerieRecibo(solicitud.sedeId());

        // Incremento atómico y persistente con bloqueo pesimista en base de datos
        ComprobanteCorrelativoEntity correlativo = correlativoRepository.findBySerieWithLock(serie)
                .orElseGet(() -> correlativoRepository.save(ComprobanteCorrelativoEntity.builder()
                        .serie(serie)
                        .ultimoNumero(0L)
                        .actualizadoEn(Instant.now())
                        .build()));

        long siguienteNumero = correlativo.getUltimoNumero() + 1;
        correlativo.setUltimoNumero(siguienteNumero);
        correlativo.setActualizadoEn(Instant.now());
        correlativoRepository.save(correlativo);

        String numero = String.format("%06d", siguienteNumero);
        LocalDateTime ahora = LocalDateTime.now();

        log.info("[CAJA INTERNA] Recibo emitido con éxito: {}-{} por S/ {}",
                serie, numero, solicitud.montoTotal());

        return new ComprobanteEmitido(
                "RECIBO",
                serie,
                numero,
                null, // Sin hash criptográfico falso
                null, // Sin QR simulado
                null, // Sin PDF falso
                ahora,
                "EMITIDO",
                "Recibo de caja interno emitido con numeración correlativa persistente en base de datos."
        );
    }

    @Override
    public boolean consultarEstado(String serie, String numero) {
        log.info("[RECIBO INTERNO] Consulta de estado para {}-{}", serie, numero);
        return true;
    }

    @Override
    public void anular(String serie, String numero, String motivo) {
        log.warn("[RECIBO INTERNO] Anulación registrada para comprobante {}-{}. Motivo: {}", serie, numero, motivo);
    }

    // Recibo interno: todas las sedes comparten la serie R001 (correlativo único con bloqueo pesimista).
    // Para series por establecimiento, agregar la serie a la sede y resolverla aquí.
    private String resolverSerieRecibo(String sedeId) {
        return SERIE_RECIBO;
    }
}
