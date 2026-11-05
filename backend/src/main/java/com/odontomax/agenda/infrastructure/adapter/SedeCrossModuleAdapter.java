package com.odontomax.agenda.infrastructure.adapter;

import com.odontomax.agenda.application.port.out.SedeValidacionPort;
import com.odontomax.agenda.domain.model.SedeAgenda;
import com.odontomax.sedes.api.ConsultaSedesModuloApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Punto único de contacto con el módulo sedes: delega en su API pública. */
@Component
@RequiredArgsConstructor
public class SedeCrossModuleAdapter implements SedeValidacionPort {

    private final ConsultaSedesModuloApi consultaSedesModuloApi;

    @Override
    public boolean existeSede(String sedeId) {
        return consultaSedesModuloApi.existeSede(sedeId);
    }

    @Override
    public Optional<SedeAgendaConfig> obtenerConfiguracionSede(String sedeId) {
        return consultaSedesModuloApi.obtenerConfiguracionSede(sedeId)
                .map(dto -> new SedeAgendaConfig(
                        dto.id(),
                        dto.nombre(),
                        dto.unidadDeNegocio(),
                        dto.activa(),
                        dto.whatsapp(),
                        dto.telefono(),
                        dto.montoAdelantoDefecto(),
                        dto.horasExpiracionAdelanto(),
                        dto.horasAnticipacionCancelacion(),
                        dto.horasRecordatorioWhatsapp(),
                        dto.horaInicioLlamadas(),
                        dto.diasVigenciaPromocionCumpleanos()
                ));
    }

    @Override
    public Optional<SedeAgenda> obtenerSede(String sedeId) {
        return consultaSedesModuloApi.obtenerSede(sedeId)
                .map(s -> new SedeAgenda(s.id(), s.activa(), s.sillones()));
    }

    @Override
    public Optional<String> obtenerUnidadDeNegocioDelServicio(String idONombre) {
        return consultaSedesModuloApi.obtenerUnidadDeNegocioDelServicio(idONombre);
    }
}
