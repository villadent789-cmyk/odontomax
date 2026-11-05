package com.odontomax.domain.port.out;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Puerto de Dominio para emisión y consulta de Comprobantes de Pago Electrónicos.
 * Sigue la arquitectura hexagonal para permitir desacoplar el core del sistema
 * del proveedor OSE/PSE autorizado por SUNAT.
 */
public interface ComprobanteElectronicoPort {

    record SolicitudEmision(
        String tipoComprobante, // "BOLETA", "FACTURA", "RECIBO"
        String sedeId,
        String clienteNombre,
        String clienteNumeroDocumento,
        String clienteTipoDocumento, // "DNI", "RUC", "SIN_DOCUMENTO"
        BigDecimal montoTotal,
        String metodoPago, // "EFECTIVO", "YAPE"
        String concepto,
        String referenciaId,
        BigDecimal saldoRestante
    ) {
        public SolicitudEmision(
            String tipoComprobante,
            String sedeId,
            String clienteNombre,
            String clienteNumeroDocumento,
            String clienteTipoDocumento,
            BigDecimal montoTotal,
            String metodoPago,
            String concepto,
            String referenciaId
        ) {
            this(tipoComprobante, sedeId, clienteNombre, clienteNumeroDocumento, clienteTipoDocumento, montoTotal, metodoPago, concepto, referenciaId, BigDecimal.ZERO);
        }
    }

    record ComprobanteEmitido(
        String tipoComprobante,
        String serie,
        String numero,
        String codigoHash,
        String qrData,
        String pdfUrlSimulada,
        LocalDateTime fechaEmision,
        String estadoSunat, // "ACEPTADO", "SIMULADO"
        String mensajeRespuesta
    ) {}

    ComprobanteEmitido emitir(SolicitudEmision solicitud);

    boolean consultarEstado(String serie, String numero);

    void anular(String serie, String numero, String motivo);
}
