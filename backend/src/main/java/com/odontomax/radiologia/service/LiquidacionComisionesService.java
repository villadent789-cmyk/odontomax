package com.odontomax.radiologia.service;

import com.odontomax.caja.api.CajaEgresoModuloApi;
import com.odontomax.identidad.api.SecurityUtils;
import com.odontomax.infrastructure.persistence.entity.ComisionFallidaEntity;
import com.odontomax.infrastructure.persistence.entity.ComisionRadiologicaEntity;
import com.odontomax.infrastructure.persistence.entity.DoctorDerivadorEntity;
import com.odontomax.infrastructure.persistence.repository.SpringDataComisionFallidaRepository;
import com.odontomax.infrastructure.persistence.repository.SpringDataComisionRadiologicaRepository;
import com.odontomax.infrastructure.persistence.repository.SpringDataDoctorDerivadorRepository;
import com.odontomax.radiologia.dto.ComisionFallidaResponseDTO;
import com.odontomax.radiologia.dto.ComisionLoteRequestDTO;
import com.odontomax.radiologia.dto.ComisionLoteResponseDTO;
import com.odontomax.radiologia.dto.EstadoCuentaDerivadorDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Pago de comisiones a los doctores que derivan: liquidación por lote (un solo egreso de caja para varias comisiones del
 * mismo doctor), estado de cuenta y las comisiones que no se pudieron generar al cobrar. Solo el administrador general.
 */
@Service
@RequiredArgsConstructor
public class LiquidacionComisionesService {

    private static final Set<String> METODOS_DE_CAJA = Set.of("EFECTIVO", "YAPE", "PLIN");
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private final SpringDataComisionRadiologicaRepository comisionRepository;
    private final SpringDataDoctorDerivadorRepository doctorRepository;
    private final SpringDataComisionFallidaRepository fallidaRepository;
    private final CajaEgresoModuloApi cajaEgresoApi;

    /** Todo o nada: si una comisión no sirve, no se liquida ninguna ni sale dinero de la caja. */
    @Transactional
    public ComisionLoteResponseDTO liquidarLote(ComisionLoteRequestDTO req) {
        Set<UUID> ids = new HashSet<>(req.getComisionIds());
        List<ComisionRadiologicaEntity> comisiones = comisionRepository.findAllById(ids);
        if (comisiones.size() != ids.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Alguna de las comisiones no existe");
        }
        String metodo = req.getMetodoPago() != null ? req.getMetodoPago().trim().toUpperCase() : "";
        if (!METODOS_DE_CAJA.contains(metodo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El método de pago debe ser EFECTIVO, YAPE o PLIN");
        }
        UUID doctorId = comisiones.get(0).getDoctorDerivadorId();
        String sedeId = comisiones.get(0).getSedeId();
        for (ComisionRadiologicaEntity c : comisiones) {
            SecurityUtils.validarAccesoSede(c.getSedeId());
            if (!"PENDIENTE".equalsIgnoreCase(c.getEstado())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solo se liquidan comisiones pendientes");
            }
            if (!doctorId.equals(c.getDoctorDerivadorId()) || !sedeId.equals(c.getSedeId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solo se liquidan juntas las comisiones de un mismo doctor");
            }
        }

        UUID loteId = UUID.randomUUID();
        OffsetDateTime ahora = OffsetDateTime.now();
        BigDecimal total = BigDecimal.ZERO;
        for (ComisionRadiologicaEntity c : comisiones) {
            c.setEstado("LIQUIDADO");
            c.setFechaLiquidacion(ahora);
            c.setMetodoPago(metodo);
            c.setCodigoOperacion(req.getCodigoOperacion());
            c.setLoteLiquidacionId(loteId);
            if (req.getNota() != null && !req.getNota().isBlank()) {
                c.setNota((c.getNota() != null ? c.getNota() + " | " : "") + req.getNota());
            }
            total = total.add(c.getMontoComision());
        }
        comisionRepository.saveAll(comisiones);

        if (total.signum() > 0) {
            String doctor = doctorRepository.findById(doctorId).map(DoctorDerivadorEntity::getNombre).orElse("doctor derivador");
            cajaEgresoApi.registrarEgresoDeSistema(sedeId, total, metodo,
                    String.format("Liquidación de %d comisiones · %s", comisiones.size(), doctor), "COMISION_RADIOLOGICA", loteId);
        }
        return ComisionLoteResponseDTO.builder().loteId(loteId).cantidad(comisiones.size()).totalPagado(total).build();
    }

    @Transactional(readOnly = true)
    public EstadoCuentaDerivadorDTO estadoCuenta(UUID doctorId, LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null || hasta.isBefore(desde)) {
            throw new IllegalArgumentException("Indica un periodo válido (desde y hasta).");
        }
        DoctorDerivadorEntity doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Doctor derivador no encontrado"));
        List<EstadoCuentaDerivadorDTO.Fila> filas = comisionRepository.findByDoctorDerivadorIdOrderByFechaEmisionDesc(doctorId).stream()
                .filter(c -> SecurityUtils.sedeEnNegocioActivo(c.getSedeId()))
                .filter(c -> {
                    LocalDate dia = c.getFechaEmision().atZoneSameInstant(LIMA).toLocalDate();
                    return !dia.isBefore(desde) && !dia.isAfter(hasta);
                })
                .map(c -> EstadoCuentaDerivadorDTO.Fila.builder()
                        .fecha(c.getFechaEmision().atZoneSameInstant(LIMA).toLocalDate())
                        .pacienteNombre(c.getPacienteNombre())
                        .estudio(c.getTipoEstudio())
                        .precioCobrado(c.getPrecioCobrado())
                        .montoComision(c.getMontoComision())
                        .estado(c.getEstado())
                        .fechaLiquidacion(c.getFechaLiquidacion() != null ? c.getFechaLiquidacion().atZoneSameInstant(LIMA).toLocalDate() : null)
                        .build())
                .toList();
        BigDecimal generado = filas.stream().map(EstadoCuentaDerivadorDTO.Fila::getMontoComision).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal pagado = filas.stream().filter(f -> "LIQUIDADO".equalsIgnoreCase(f.getEstado()))
                .map(EstadoCuentaDerivadorDTO.Fila::getMontoComision).reduce(BigDecimal.ZERO, BigDecimal::add);
        return EstadoCuentaDerivadorDTO.builder()
                .doctorId(doctor.getId())
                .doctorNombre(doctor.getNombre())
                .clinicaOrigen(doctor.getClinicaOrigen())
                .desde(desde)
                .hasta(hasta)
                .filas(filas)
                .totalGenerado(generado)
                .totalPagado(pagado)
                .totalPendiente(generado.subtract(pagado))
                .build();
    }

    // ---- comisiones que no se pudieron generar al cobrar ----

    /** Se guarda en su propia transacción: el cobro que la originó no debe perder ni arrastrar este registro. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarFalloAlGenerar(UUID citaId, String sedeId, String pacienteNombre, String estudio, String motivo) {
        ComisionFallidaEntity fila = fallidaRepository.findById(citaId).orElse(null);
        String texto = motivo != null && !motivo.isBlank() ? motivo : "Error desconocido";
        if (fila == null) {
            fallidaRepository.save(ComisionFallidaEntity.builder().citaId(citaId).sedeId(sedeId)
                    .pacienteNombre(pacienteNombre).estudio(estudio).motivo(texto).build());
        } else {
            fila.setMotivo(texto);
            fila.setIntentos(fila.getIntentos() + 1);
            fila.setActualizadoEn(OffsetDateTime.now());
            fallidaRepository.save(fila);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void resolverFallo(UUID citaId) {
        fallidaRepository.deleteById(citaId);
    }

    @Transactional(readOnly = true)
    public List<ComisionFallidaResponseDTO> listarFallidas() {
        return fallidaRepository.findAllByOrderByCreadoEnAsc().stream()
                .filter(f -> SecurityUtils.sedeEnNegocioActivo(f.getSedeId()))
                .map(f -> ComisionFallidaResponseDTO.builder()
                        .citaId(f.getCitaId())
                        .pacienteNombre(f.getPacienteNombre())
                        .estudio(f.getEstudio())
                        .motivo(f.getMotivo())
                        .intentos(f.getIntentos())
                        .creadoEn(f.getCreadoEn())
                        .build())
                .toList();
    }
}
