package com.odontomax.application.service;

import com.odontomax.application.dto.request.GastoAnulacionRequestDTO;
import com.odontomax.application.dto.request.GastoCreateRequestDTO;
import com.odontomax.application.dto.response.GastoResponseDTO;
import com.odontomax.domain.port.out.StoragePort;
import com.odontomax.infrastructure.persistence.entity.AuditoriaLogEntity;
import com.odontomax.infrastructure.persistence.entity.CajaMovimientoEntity;
import com.odontomax.infrastructure.persistence.entity.CajaSesionEntity;
import com.odontomax.infrastructure.persistence.entity.GastoEntity;
import com.odontomax.infrastructure.persistence.repository.SpringDataAuditoriaRepository;
import com.odontomax.infrastructure.persistence.repository.SpringDataCajaMovimientoRepository;
import com.odontomax.infrastructure.persistence.repository.SpringDataCajaSesionRepository;
import com.odontomax.infrastructure.persistence.repository.SpringDataGastoRepository;
import com.odontomax.identidad.api.ClinicaUserPrincipal;
import com.odontomax.identidad.api.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GastoService {

    private final SpringDataGastoRepository gastoRepository;
    private final SpringDataCajaSesionRepository cajaSesionRepository;
    private final SpringDataCajaMovimientoRepository cajaMovimientoRepository;
    private final SpringDataAuditoriaRepository auditoriaRepository;
    private final Optional<StoragePort> storagePort;
    private final CajaService cajaService;

    @org.springframework.beans.factory.annotation.Value("${caja.egreso.tope-movimiento:200.00}")
    private java.math.BigDecimal topeMovimiento = new java.math.BigDecimal("200.00");

    @Transactional(readOnly = true)
    public List<GastoResponseDTO> listarPorSede(String sedeId) {
        SecurityUtils.validarAccesoSede(sedeId);
        return gastoRepository.findBySedeIdOrderByGastadoEnDesc(sedeId)
                .stream()
                .map(this::toResponseDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public GastoResponseDTO obtenerPorId(UUID id) {
        GastoEntity gasto = gastoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Gasto no encontrado con ID: " + id));

        SecurityUtils.validarAccesoSede(gasto.getSedeId());
        return toResponseDTO(gasto);
    }

    @Transactional
    public GastoResponseDTO registrarGasto(GastoCreateRequestDTO dto) {
        ClinicaUserPrincipal currentUser = SecurityUtils.getCurrentUser();
        if (currentUser == null) {
            throw new AccessDeniedException("No autenticado");
        }

        SecurityUtils.validarAccesoSede(dto.getSedeId());

        if (dto.getConcepto() == null || dto.getConcepto().trim().length() < 3) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "El concepto o motivo del gasto es obligatorio."
            );
        }

        UUID sesionCajaId = null;
        if ("EFECTIVO".equalsIgnoreCase(dto.getMetodoPago())) {
            if (dto.getMonto() != null && dto.getMonto().compareTo(topeMovimiento) > 0) {
                boolean esAdmin = "ADMIN_GENERAL".equals(currentUser.rol()) || "ADMIN_SEDE".equals(currentUser.rol());
                if (!esAdmin) {
                    throw new org.springframework.web.server.ResponseStatusException(
                            org.springframework.http.HttpStatus.FORBIDDEN,
                            String.format("Los gastos en efectivo mayores a S/ %s requieren autorización de un Administrador de Sede o Administrador General.", topeMovimiento)
                    );
                }
            }

            Optional<CajaSesionEntity> sesionAbierta = cajaSesionRepository
                    .findBySedeIdAndAbiertaPorAndCerradaEnIsNull(dto.getSedeId(), currentUser.id())
                    .or(() -> cajaSesionRepository.findBySedeIdAndCerradaEnIsNull(dto.getSedeId()));

            // Sin caja abierta se abre automáticamente con fondo S/ 0.00 (sedes sin cajero dedicado)
            sesionCajaId = sesionAbierta.isPresent()
                    ? sesionAbierta.get().getId()
                    : cajaService.obtenerOAbrirSesionAutomatica(dto.getSedeId()).getId();
        }

        GastoEntity gasto = GastoEntity.builder()
                .sedeId(dto.getSedeId())
                .categoria(dto.getCategoria().toUpperCase())
                .concepto(dto.getConcepto())
                .monto(dto.getMonto())
                .metodoPago(dto.getMetodoPago().toUpperCase())
                .comprobante(dto.getComprobante())
                .comprobanteKey(dto.getComprobanteKey())
                .cajaSesionId(sesionCajaId)
                .registradoPor(currentUser.id())
                .anulado(false)
                .build();

        GastoEntity guardado = gastoRepository.save(gasto);

        if (sesionCajaId != null && "EFECTIVO".equalsIgnoreCase(dto.getMetodoPago())) {
            cajaSesionRepository.findById(sesionCajaId).ifPresent(sesion -> {
                cajaMovimientoRepository.save(CajaMovimientoEntity.builder()
                        .sesion(sesion)
                        .tipo("EGRESO")
                        .concepto(String.format("Gasto [%s]: %s", guardado.getCategoria(), guardado.getConcepto()))
                        .monto(guardado.getMonto())
                        .metodoPago("EFECTIVO")
                        .referenciaTipo("GASTO")
                        .referenciaId(guardado.getId())
                        .registradoPor(currentUser.id())
                        .build());
            });
        }

        auditoriaRepository.save(AuditoriaLogEntity.builder()
                .usuarioId(currentUser.id())
                .usuarioNombre(currentUser.nombre())
                .rol(currentUser.rol())
                .sedeId(dto.getSedeId())
                .modulo("GASTOS")
                .accion("REGISTRAR_GASTO")
                .tabla("gastos")
                .registroId(guardado.getId().toString())
                .detalle(String.format("Gasto registrado por S/ %s en categoría %s: %s",
                        guardado.getMonto(), guardado.getCategoria(), guardado.getConcepto()))
                .build());

        return toResponseDTO(guardado);
    }

    @Transactional
    public GastoResponseDTO anularGasto(UUID id, GastoAnulacionRequestDTO dto) {
        ClinicaUserPrincipal currentUser = SecurityUtils.getCurrentUser();
        if (currentUser == null) {
            throw new AccessDeniedException("No autenticado");
        }

        GastoEntity gasto = gastoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Gasto no encontrado"));

        SecurityUtils.validarAccesoSede(gasto.getSedeId());

        if (Boolean.TRUE.equals(gasto.getAnulado())) {
            throw new IllegalStateException("El gasto ya ha sido anulado previamente");
        }

        gasto.setAnulado(true);
        gasto.setMotivoAnulacion(dto.getMotivoAnulacion());

        GastoEntity actualizado = gastoRepository.save(gasto);

        if ("EFECTIVO".equalsIgnoreCase(gasto.getMetodoPago()) && gasto.getCajaSesionId() != null) {
            cajaSesionRepository.findById(gasto.getCajaSesionId()).ifPresent(sesion -> {
                if (sesion.getCerradaEn() == null) {
                    cajaMovimientoRepository.save(CajaMovimientoEntity.builder()
                            .sesion(sesion)
                            .tipo("INGRESO")
                            .concepto("Compensación anulación de gasto: " + dto.getMotivoAnulacion())
                            .monto(gasto.getMonto())
                            .metodoPago("EFECTIVO")
                            .referenciaTipo("GASTO")
                            .referenciaId(gasto.getId())
                            .registradoPor(currentUser.id())
                            .build());
                }
            });
        }

        auditoriaRepository.save(AuditoriaLogEntity.builder()
                .usuarioId(currentUser.id())
                .usuarioNombre(currentUser.nombre())
                .rol(currentUser.rol())
                .sedeId(gasto.getSedeId())
                .modulo("GASTOS")
                .accion("ANULAR_GASTO")
                .tabla("gastos")
                .registroId(id.toString())
                .detalle("Gasto anulado. Motivo: " + dto.getMotivoAnulacion())
                .build());

        return toResponseDTO(actualizado);
    }

    private GastoResponseDTO toResponseDTO(GastoEntity g) {
        String urlComprobante = null;
        if (g.getComprobanteKey() != null && storagePort.isPresent()) {
            try {
                urlComprobante = storagePort.get().generatePresignedUrl(g.getComprobanteKey(), 15);
            } catch (Exception ignored) {}
        }

        return GastoResponseDTO.builder()
                .id(g.getId())
                .sedeId(g.getSedeId())
                .sedeNombre(g.getSedeId())
                .categoria(g.getCategoria())
                .concepto(g.getConcepto())
                .monto(g.getMonto())
                .metodoPago(g.getMetodoPago())
                .comprobante(g.getComprobante())
                .comprobanteKey(g.getComprobanteKey())
                .comprobanteUrl(urlComprobante)
                .cajaSesionId(g.getCajaSesionId())
                .gastadoEn(g.getGastadoEn())
                .registradoPor(g.getRegistradoPor())
                .anulado(g.getAnulado())
                .motivoAnulacion(g.getMotivoAnulacion())
                .creadoEn(g.getCreadoEn())
                .build();
    }
}
