package com.odontomax.promociones.application.port.in;

import com.odontomax.promociones.application.dto.PromocionCumpleanosDTO;
import com.odontomax.promociones.application.dto.ReportePromocionesDTO;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConsultarPromocionUseCase {
    Optional<PromocionCumpleanosDTO> obtenerPromocionVigente(UUID pacienteId);
    List<PromocionCumpleanosDTO> listarPromocionesPaciente(UUID pacienteId);
    ReportePromocionesDTO generarReporte(LocalDate desde, LocalDate hasta, String sedeId);
}
