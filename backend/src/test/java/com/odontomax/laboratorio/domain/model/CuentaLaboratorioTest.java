package com.odontomax.laboratorio.domain.model;

import com.odontomax.laboratorio.domain.exception.PagoLaboratorioNoPermitidoException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CuentaLaboratorioTest {
    @Test
    void derivaEstadoPendienteParcialYPagado() {
        BigDecimal costo = new BigDecimal("200.00");

        assertThat(EstadoPagoLaboratorio.derivar(costo, BigDecimal.ZERO)).isEqualTo(EstadoPagoLaboratorio.PENDIENTE);
        assertThat(EstadoPagoLaboratorio.derivar(costo, new BigDecimal("50.00"))).isEqualTo(EstadoPagoLaboratorio.PARCIAL);
        assertThat(EstadoPagoLaboratorio.derivar(costo, costo)).isEqualTo(EstadoPagoLaboratorio.PAGADO);
    }

    @Test
    void rechazaPagoSinCostoOrdenCanceladaYMontoMayorAlSaldo() {
        assertThatThrownBy(() -> CuentaLaboratorio.validarPago("ENVIADO", null, BigDecimal.ZERO, BigDecimal.ONE))
                .isInstanceOf(PagoLaboratorioNoPermitidoException.class)
                .hasMessageContaining("no tiene costo");
        assertThatThrownBy(() -> CuentaLaboratorio.validarPago("CANCELADO", new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ONE))
                .isInstanceOf(PagoLaboratorioNoPermitidoException.class)
                .hasMessageContaining("cancelada");
        assertThatThrownBy(() -> CuentaLaboratorio.validarPago("ENVIADO", new BigDecimal("100"),
                new BigDecimal("60"), new BigDecimal("41")))
                .isInstanceOf(PagoLaboratorioNoPermitidoException.class)
                .hasMessageContaining("S/ 40");
    }

    @Test
    void rechazaAnulacionSiYaEstaAnulado() {
        assertThatThrownBy(() -> CuentaLaboratorio.validarAnulacion(true, "Error de tipeo"))
                .isInstanceOf(PagoLaboratorioNoPermitidoException.class)
                .hasMessageContaining("ya ha sido anulado previamente");
    }

    @Test
    void rechazaAnulacionSinMotivoObligatorio() {
        assertThatThrownBy(() -> CuentaLaboratorio.validarAnulacion(false, null))
                .isInstanceOf(PagoLaboratorioNoPermitidoException.class)
                .hasMessageContaining("El motivo de anulación es obligatorio");

        assertThatThrownBy(() -> CuentaLaboratorio.validarAnulacion(false, "   "))
                .isInstanceOf(PagoLaboratorioNoPermitidoException.class)
                .hasMessageContaining("El motivo de anulación es obligatorio");
    }

    @Test
    void permiteAnulacionValida() {
        assertThatCode(() -> CuentaLaboratorio.validarAnulacion(false, "Pago duplicado"))
                .doesNotThrowAnyException();
    }
}
