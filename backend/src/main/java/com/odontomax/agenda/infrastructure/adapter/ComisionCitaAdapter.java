package com.odontomax.agenda.infrastructure.adapter;

import com.odontomax.agenda.application.port.out.ComisionCitaPort;
import com.odontomax.agenda.application.port.out.SedeValidacionPort;
import com.odontomax.agenda.domain.model.Cita;
import com.odontomax.agenda.application.port.out.CitaRepositoryPort;
import com.odontomax.radiologia.dto.ComisionRadiologicaCreateRequestDTO;
import com.odontomax.radiologia.service.ComisionRadiologicaService;
import com.odontomax.radiologia.service.LiquidacionComisionesService;
import com.odontomax.infrastructure.persistence.repository.SpringDataComisionRadiologicaRepository;
import com.odontomax.tratamientos.service.TratamientoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Adaptador que comunica el ciclo de vida de la cita de Villamax con el módulo de radiología
 * para generar o anular comisiones radiológicas de forma automática e idempotente.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ComisionCitaAdapter implements ComisionCitaPort {

    private final CitaRepositoryPort citaRepository;
    private final SedeValidacionPort sedeValidacionPort;
    private final ComisionRadiologicaService comisionService;
    private final LiquidacionComisionesService liquidacionService;
    private final SpringDataComisionRadiologicaRepository comisionRepository;
    private final TratamientoService tratamientoService;

    @Override
    public void generarComisionSiAplica(UUID citaId) {
        if (citaId == null) return;
        var citaOpt = citaRepository.findById(citaId);
        if (citaOpt.isEmpty()) return;
        Cita cita = citaOpt.get();

        // Solo aplica a Villamax
        boolean esVillamax = sedeValidacionPort.obtenerConfiguracionSede(cita.getSedeId())
                .map(c -> "VILLAMAX".equalsIgnoreCase(c.unidadDeNegocio()))
                .orElseGet(() -> cita.getSedeId() != null && cita.getSedeId().toLowerCase().contains("villamax"));
        if (!esVillamax) {
            cerrarFallo(citaId);
            return;
        }

        // Paciente directo = sin comisión
        if (cita.isPacienteDirecto()) {
            log.info("Cita {} de Villamax corresponde a paciente directo, no genera comisión.", cita.getId());
            cerrarFallo(citaId);
            return;
        }

        // Sin doctor derivador = sin comisión
        if (cita.getDoctorDerivadorId() == null) {
            log.info("Cita {} de Villamax no tiene doctor derivador asignado.", cita.getId());
            cerrarFallo(citaId);
            return;
        }

        // Idempotente: si ya existe comisión para esta cita o tratamiento, no duplicar
        if (comisionRepository.findByCitaId(cita.getId()).isPresent()) {
            log.info("Comisión ya registrada previamente para la cita {}", cita.getId());
            return;
        }
        if (cita.getTratamientoId() != null && comisionRepository.findByTratamientoId(cita.getTratamientoId()).isPresent()) {
            log.info("Comisión ya registrada previamente para el tratamiento {}", cita.getTratamientoId());
            return;
        }

        // Monto realmente cobrado (precio - descuento)
        BigDecimal montoCobrado = null;
        if (cita.getTratamientoId() != null) {
            var tratOpt = tratamientoService.buscarParaCita(cita.getTratamientoId());
            if (tratOpt.isPresent()) {
                var trat = tratOpt.get();
                // Si aún tiene saldo pendiente, la comisión nace cuando quede cobrado por completo
                BigDecimal saldo = trat.getSaldoPendiente() != null ? trat.getSaldoPendiente() : BigDecimal.ZERO;
                if (saldo.compareTo(BigDecimal.ZERO) > 0) {
                    log.info("Cita {} de Villamax aún tiene saldo pendiente de S/ {}. La comisión nacerá al completar el cobro.",
                            cita.getId(), saldo);
                    return;
                }
                montoCobrado = trat.getCostoTotal() != null ? trat.getCostoTotal() : BigDecimal.ZERO;
            }
        }

        try {
            comisionService.registrarComision(ComisionRadiologicaCreateRequestDTO.builder()
                    .doctorDerivadorId(cita.getDoctorDerivadorId())
                    .pacienteNombre(cita.getPacienteNombre())
                    .pacienteId(cita.getPacienteId())
                    .estudioId(cita.getMotivoConsulta())
                    .tipoEstudio(cita.getMotivoConsulta())
                    .precioCobrado(montoCobrado)
                    .citaId(cita.getId())
                    .tratamientoId(cita.getTratamientoId())
                    .sedeId(cita.getSedeId())
                    .nota("Generada automáticamente al cobrar estudio de cita " + cita.getId())
                    .build(), null);
            log.info("Comisión automática generada exitosamente para la cita {}", cita.getId());
            cerrarFallo(citaId);
        } catch (Exception e) {
            log.error("Error al registrar comisión automática para la cita {}: {}", cita.getId(), e.getMessage(), e);
            // El cobro sigue su curso, pero el administrador ve la comisión pendiente de generar y puede reintentarla.
            liquidacionService.registrarFalloAlGenerar(cita.getId(), cita.getSedeId(), cita.getPacienteNombre(),
                    cita.getMotivoConsulta(), e.getMessage());
        }
    }

    private void cerrarFallo(UUID citaId) {
        try {
            liquidacionService.resolverFallo(citaId);
        } catch (RuntimeException e) {
            log.warn("No se pudo cerrar el aviso de comisión pendiente de la cita {}: {}", citaId, e.getMessage());
        }
    }

    @Override
    public void anularComisionSiAplica(UUID citaId, String motivo) {
        if (citaId == null) return;
        comisionService.anularComisionPorCita(citaId, motivo);
    }

    @Override
    public void anularComisionPorTratamientoSiAplica(UUID tratamientoId, String motivo) {
        if (tratamientoId == null) return;
        comisionService.anularComisionPorTratamiento(tratamientoId, motivo);
    }
}
