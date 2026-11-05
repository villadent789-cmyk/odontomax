package com.odontomax.promociones.application.port.out;

import com.odontomax.promociones.domain.model.PromocionCumpleanos;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PromocionRepositoryPort {
    Optional<PromocionCumpleanos> findById(UUID id);
    Optional<PromocionCumpleanos> findByPacienteIdAndAnio(UUID pacienteId, int anio);
    Optional<PromocionCumpleanos> findVigenteByPacienteId(UUID pacienteId, LocalDate hoy);
    List<PromocionCumpleanos> findByPacienteId(UUID pacienteId);
    List<PromocionCumpleanos> findByRangoFechasAndSede(LocalDate desde, LocalDate hasta, String sedeId);
    PromocionCumpleanos save(PromocionCumpleanos promocion);
}
