package com.odontomax.infrastructure.adapter;

import com.odontomax.application.dto.response.CajaResumenDiarioDTO;
import com.odontomax.application.port.out.CajaPeriodoPort;
import com.odontomax.application.port.out.CajaTotalesSesion;
import com.odontomax.infrastructure.persistence.entity.CajaMovimientoEntity;
import com.odontomax.infrastructure.persistence.entity.CajaSesionEntity;
import com.odontomax.infrastructure.persistence.repository.SpringDataCajaMovimientoRepository;
import com.odontomax.infrastructure.persistence.repository.SpringDataCajaSesionRepository;
import com.odontomax.infrastructure.persistence.repository.SpringDataPagoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CajaPeriodoJpaAdapter implements CajaPeriodoPort {

    private static final List<String> METODOS = List.of("EFECTIVO", "YAPE", "PLIN", "TARJETA_DEBITO", "TARJETA_CREDITO");
    private static final ZoneId ZONE_LIMA = ZoneId.of("America/Lima");

    private final SpringDataCajaMovimientoRepository movimientoRepository;
    private final SpringDataCajaSesionRepository sesionRepository;
    private final SpringDataPagoRepository pagoRepository;

    @Override
    @Transactional(readOnly = true)
    public CajaTotalesSesion calcularTotales(UUID sesionId) {
        Map<String, BigDecimal[]> totales = new HashMap<>();
        for (String metodo : METODOS) {
            totales.put(metodo, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
        }
        for (CajaMovimientoEntity movimiento : movimientoRepository.findBySesionIdOrderByRegistradoEnAsc(sesionId)) {
            BigDecimal[] metodo = totales.get(movimiento.getMetodoPago().toUpperCase());
            if (metodo != null) {
                int indice = "INGRESO".equalsIgnoreCase(movimiento.getTipo()) ? 0 : 1;
                metodo[indice] = metodo[indice].add(movimiento.getMonto());
            }
        }
        for (String metodo : METODOS) {
            BigDecimal pagosSinMovimiento = pagoRepository.sumPagosSinMovimientoPorSesionYMetodo(sesionId, metodo);
            totales.get(metodo)[0] = totales.get(metodo)[0].add(ceroSiNulo(pagosSinMovimiento));
        }

        CajaSesionEntity sesion = sesionRepository.findById(sesionId)
                .orElseThrow(() -> new IllegalArgumentException("Sesión de caja no encontrada"));
        BigDecimal esperadoYape = sesion.getMontoEsperadoYape() != null
                ? sesion.getMontoEsperadoYape() : esperado(totales.get("YAPE"));
        BigDecimal esperadoPlin = sesion.getMontoEsperadoPlin() != null
                ? sesion.getMontoEsperadoPlin() : esperado(totales.get("PLIN"));
        BigDecimal contadoYape = sesion.getMontoContadoYape();
        BigDecimal contadoPlin = sesion.getMontoContadoPlin();
        BigDecimal ingresosTarjeta = totales.get("TARJETA_DEBITO")[0].add(totales.get("TARJETA_CREDITO")[0]);
        BigDecimal egresosTarjeta = totales.get("TARJETA_DEBITO")[1].add(totales.get("TARJETA_CREDITO")[1]);
        BigDecimal esperadoTarjeta = sesion.getMontoEsperadoTarjeta() != null
                ? sesion.getMontoEsperadoTarjeta() : ingresosTarjeta.subtract(egresosTarjeta);
        BigDecimal contadoTarjeta = sesion.getMontoContadoTarjeta();

        return new CajaTotalesSesion(
                totales.get("EFECTIVO")[0], totales.get("EFECTIVO")[1],
                totales.get("YAPE")[0], totales.get("YAPE")[1],
                totales.get("PLIN")[0], totales.get("PLIN")[1],
                esperadoYape, contadoYape, contadoYape != null ? contadoYape.subtract(esperadoYape) : null,
                esperadoPlin, contadoPlin, contadoPlin != null ? contadoPlin.subtract(esperadoPlin) : null,
                totales.get("TARJETA_DEBITO")[0], totales.get("TARJETA_CREDITO")[0], egresosTarjeta,
                esperadoTarjeta, contadoTarjeta, contadoTarjeta != null ? contadoTarjeta.subtract(esperadoTarjeta) : null);
    }

    @Override
    @Transactional
    public void guardarCierreMetodos(UUID sesionId, BigDecimal montoEsperadoYape, BigDecimal montoContadoYape,
                                      BigDecimal montoEsperadoPlin, BigDecimal montoContadoPlin,
                                      BigDecimal montoEsperadoTarjeta, BigDecimal montoContadoTarjeta) {
        CajaSesionEntity sesion = sesionRepository.findById(sesionId)
                .orElseThrow(() -> new IllegalArgumentException("Sesión de caja no encontrada"));
        sesion.setMontoEsperadoYape(montoEsperadoYape);
        sesion.setMontoContadoYape(montoContadoYape);
        sesion.setMontoEsperadoPlin(montoEsperadoPlin);
        sesion.setMontoContadoPlin(montoContadoPlin);
        sesion.setMontoEsperadoTarjeta(montoEsperadoTarjeta);
        sesion.setMontoContadoTarjeta(montoContadoTarjeta);
    }

    @Override
    @Transactional(readOnly = true)
    public CajaResumenDiarioDTO obtenerResumenDiario(String sedeId, Instant desde, Instant hasta) {
        return CajaResumenDiarioDTO.builder()
                .fecha(desde.atZone(ZONE_LIMA).toLocalDate())
                .sedeId(sedeId)
                .ingresosEfectivo(pagoRepository.sumPorSedeFechaYMetodo(sedeId, desde, hasta, "EFECTIVO"))
                .ingresosYape(pagoRepository.sumPorSedeFechaYMetodo(sedeId, desde, hasta, "YAPE"))
                .ingresosPlin(pagoRepository.sumPorSedeFechaYMetodo(sedeId, desde, hasta, "PLIN"))
                .egresosEfectivo(movimientoRepository.sumEgresosSedeFechaYMetodo(sedeId, desde, hasta, "EFECTIVO"))
                .egresosYape(movimientoRepository.sumEgresosSedeFechaYMetodo(sedeId, desde, hasta, "YAPE"))
                .egresosPlin(movimientoRepository.sumEgresosSedeFechaYMetodo(sedeId, desde, hasta, "PLIN"))
                .ingresosTarjetaDebito(pagoRepository.sumPorSedeFechaYMetodo(sedeId, desde, hasta, "TARJETA_DEBITO"))
                .ingresosTarjetaCredito(pagoRepository.sumPorSedeFechaYMetodo(sedeId, desde, hasta, "TARJETA_CREDITO"))
                .egresosTarjeta(movimientoRepository.sumEgresosSedeFechaYMetodo(sedeId, desde, hasta, "TARJETA_DEBITO")
                        .add(movimientoRepository.sumEgresosSedeFechaYMetodo(sedeId, desde, hasta, "TARJETA_CREDITO")))
                .build();
    }

    private static BigDecimal esperado(BigDecimal[] totales) {
        return totales[0].subtract(totales[1]);
    }

    private static BigDecimal ceroSiNulo(BigDecimal valor) {
        return valor != null ? valor : BigDecimal.ZERO;
    }
}
