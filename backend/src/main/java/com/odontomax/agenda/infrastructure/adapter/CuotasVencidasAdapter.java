package com.odontomax.agenda.infrastructure.adapter;

import com.odontomax.agenda.application.port.out.CuotasVencidasPort;
import com.odontomax.tratamientos.api.CuotasVencidasModuloApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
public class CuotasVencidasAdapter implements CuotasVencidasPort {

    private final CuotasVencidasModuloApi cuotasVencidasModuloApi;

    @Override
    public List<CuotaVencida> cuotasVencidasDeSede(String sedeId, LocalDate hoy) {
        return cuotasVencidasModuloApi.cuotasVencidasDeSede(sedeId, hoy).stream()
                .map(c -> new CuotaVencida(c.tratamientoId(), c.pacienteId(), c.pacienteNombre(), c.pacienteTelefono(),
                        c.tratamiento(), c.concepto(), c.venceEn(), c.saldo()))
                .toList();
    }
}
