package com.odontomax.infrastructure.adapter;

import com.odontomax.application.port.out.ReporteCajaPort;
import com.odontomax.infrastructure.persistence.repository.SpringDataCajaMovimientoRepository;
import com.odontomax.infrastructure.persistence.repository.SpringDataCajaSesionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ReporteCajaJpaAdapter implements ReporteCajaPort {

    private final SpringDataCajaMovimientoRepository movimientoRepository;
    private final SpringDataCajaSesionRepository sesionRepository;

    @Override
    @Transactional(readOnly = true)
    public List<EgresoPorOrigen> egresosPorOrigen(String sedeId, Instant desde, Instant hasta) {
        return movimientoRepository.sumEgresosPorCategoria(sedeId, desde, hasta).stream()
                .map(fila -> new EgresoPorOrigen((String) fila[0], (BigDecimal) fila[1]))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CierreDePeriodo> cierresDelPeriodo(String sedeId, Instant desde, Instant hasta) {
        return sesionRepository.findBySedeIdAndCerradaEnGreaterThanEqualAndCerradaEnLessThanOrderByCerradaEnAsc(sedeId, desde, hasta)
                .stream()
                .map(s -> new CierreDePeriodo(s.getCerradaEn(),
                        s.getMontoEsperado(), s.getMontoContado(),
                        s.getMontoEsperadoYape(), s.getMontoContadoYape(),
                        s.getMontoEsperadoPlin(), s.getMontoContadoPlin(),
                        s.getMontoEsperadoTarjeta(), s.getMontoContadoTarjeta(),
                        s.getObservaciones()))
                .toList();
    }
}
