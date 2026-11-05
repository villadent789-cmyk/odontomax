package com.odontomax.agenda.infrastructure.adapter;

import com.odontomax.agenda.application.port.out.PagoAdelantoPort;
import com.odontomax.agenda.application.port.out.RegistroAuditoriaPort;
import com.odontomax.agenda.application.port.out.SaldoFavorPort;
import com.odontomax.agenda.application.port.out.TratamientoCitaPort;
import com.odontomax.agenda.infrastructure.persistence.entity.CitaEntity;
import com.odontomax.agenda.infrastructure.persistence.entity.PacienteSaldoFavorEntity;
import com.odontomax.agenda.infrastructure.persistence.repository.SpringDataCitaRepository;
import com.odontomax.agenda.infrastructure.persistence.repository.SpringDataPacienteSaldoFavorRepository;
import com.odontomax.application.service.CajaService;
import com.odontomax.identidad.api.SecurityUtils;
import com.odontomax.infrastructure.persistence.entity.CajaMovimientoEntity;
import com.odontomax.infrastructure.persistence.entity.CajaSesionEntity;
import com.odontomax.infrastructure.persistence.entity.PagoEntity;
import com.odontomax.infrastructure.persistence.repository.SpringDataCajaMovimientoRepository;
import com.odontomax.infrastructure.persistence.repository.SpringDataCajaSesionRepository;
import com.odontomax.infrastructure.persistence.repository.SpringDataPagoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PagoAdelantoManualAdapter implements PagoAdelantoPort, SaldoFavorPort {

    private final SpringDataPacienteSaldoFavorRepository saldoFavorRepository;
    private final RegistroAuditoriaPort auditoriaPort;
    private final SpringDataPagoRepository pagoRepository;
    private final SpringDataCajaSesionRepository cajaSesionRepository;
    private final SpringDataCajaMovimientoRepository cajaMovimientoRepository;
    private final SpringDataCitaRepository citaRepository;
    private final TratamientoCitaPort tratamientoCitaPort;
    private final CajaService cajaService;

    @Override
    @Transactional
    public void registrarAdelanto(UUID citaId, UUID pacienteId, UUID tratamientoId, String sedeId,
                                  BigDecimal monto, String medioPago, String numeroOperacion, String usuario) {
        auditoriaPort.registrar(
                "REGISTRO_ADELANTO_MANUAL",
                "CITAS",
                sedeId,
                String.format("Adelanto registrado para cita %s: S/ %s vía %s (Op: %s) por %s",
                        citaId, monto, medioPago, numeroOperacion != null ? numeroOperacion : "-", usuario)
        );

        if (pacienteId == null || monto == null || monto.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        if (tratamientoId != null) {
            tratamientoCitaPort.validarSaldoParaCobro(pacienteId, tratamientoId, monto);
        } else {
            // shortcut: las reservas sin doctor conservan el adelanto sin plan; mejorar cuando toda reserva exija doctor.
        }

        String metodo = (medioPago != null && !medioPago.isBlank()) ? medioPago.toUpperCase().trim() : "YAPE";
        if (!java.util.Set.of("EFECTIVO", "YAPE", "PLIN", "TARJETA_DEBITO", "TARJETA_CREDITO").contains(metodo)) {
            throw new IllegalArgumentException("El método de pago del adelanto debe ser EFECTIVO, YAPE, PLIN, TARJETA_DEBITO o TARJETA_CREDITO");
        }

        UUID usuarioId = SecurityUtils.getCurrentUser() != null ? SecurityUtils.getCurrentUser().id() : null;
        Optional<CajaSesionEntity> sesionActiva = cajaSesionRepository.findBySedeIdAndCerradaEnIsNull(sedeId);
        CajaSesionEntity sesion = sesionActiva.orElseGet(() -> cajaService.obtenerOAbrirSesionAutomatica(sedeId));
        UUID sesionCajaId = sesion.getId();

        String numeroOpLimpio = (numeroOperacion != null && !numeroOperacion.isBlank())
                ? numeroOperacion.trim()
                : "OP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        PagoEntity pago = PagoEntity.builder()
                .sedeId(sedeId)
                .pacienteId(pacienteId)
                .tratamientoId(tratamientoId)
                .monto(monto)
                .metodoPago(metodo)
                .numeroOperacion(numeroOpLimpio)
                .comprobante("ADELANTO-" + citaId)
                .comprobanteTipo("RECIBO")
                .cajaSesionId(sesionCajaId)
                .especialistaId(citaRepository.findById(citaId).map(CitaEntity::getDoctorId).orElse(null))
                .registradoPor(usuarioId)
                .anulado(false)
                .build();

        pagoRepository.save(pago);

        cajaMovimientoRepository.save(CajaMovimientoEntity.builder()
                .sesion(sesion)
                .tipo("INGRESO")
                .concepto("Adelanto cita " + (citaId != null ? citaId.toString().substring(0, 8) : ""))
                .monto(monto)
                .metodoPago(metodo)
                .referenciaTipo("PAGO_TRATAMIENTO")
                .referenciaId(pago.getId())
                .registradoPor(usuarioId != null ? usuarioId : sesion.getAbiertaPor())
                .build());
    }

    @Override
    @Transactional
    public void acreditarSaldoFavor(UUID pacienteId, UUID citaId, BigDecimal monto, String motivo) {
        if (monto == null || monto.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        PacienteSaldoFavorEntity saldo = PacienteSaldoFavorEntity.builder()
                .pacienteId(pacienteId)
                .citaOrigenId(citaId)
                .monto(monto)
                .motivo(motivo != null ? motivo.trim() : "Cancelación anticipada de cita con adelanto")
                .estado("DISPONIBLE")
                .build();
        saldoFavorRepository.save(saldo);
    }
}
