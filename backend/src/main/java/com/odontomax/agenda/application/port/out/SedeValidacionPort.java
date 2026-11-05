package com.odontomax.agenda.application.port.out;

import com.odontomax.agenda.domain.model.SedeAgenda;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.Optional;

public interface SedeValidacionPort {

    boolean existeSede(String sedeId);

    record SedeAgendaConfig(
            String id,
            String nombre,
            String unidadDeNegocio,
            boolean activa,
            String whatsapp,
            String telefono,
            BigDecimal montoAdelantoDefecto,
            int horasExpiracionAdelanto,
            int horasAnticipacionCancelacion,
            int horasRecordatorioWhatsapp,
            LocalTime horaInicioLlamadas,
            int diasVigenciaPromocionCumpleanos
    ) {
        public boolean requiereAdelanto() {
            return !"VILLAMAX".equalsIgnoreCase(unidadDeNegocio)
                    && montoAdelantoDefecto != null
                    && montoAdelantoDefecto.compareTo(BigDecimal.ZERO) > 0;
        }
    }

    Optional<SedeAgendaConfig> obtenerConfiguracionSede(String sedeId);

    /** Estado y sillones de la sede (activa o inactiva); vacío si no existe. */
    Optional<SedeAgenda> obtenerSede(String sedeId);

    /** Unidad de negocio (VILLADENT / VILLAMAX) del servicio por id o nombre exacto; vacío si no existe. */
    Optional<String> obtenerUnidadDeNegocioDelServicio(String idONombre);
}
