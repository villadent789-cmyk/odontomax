package com.odontomax.caja.infrastructure.adapter;

import com.odontomax.application.service.CajaService;
import com.odontomax.caja.api.CajaEgresoModuloApi;
import com.odontomax.identidad.api.ClinicaUserPrincipal;
import com.odontomax.identidad.api.SecurityUtils;
import com.odontomax.infrastructure.persistence.entity.AuditoriaLogEntity;
import com.odontomax.infrastructure.persistence.entity.CajaMovimientoEntity;
import com.odontomax.infrastructure.persistence.entity.CajaSesionEntity;
import com.odontomax.infrastructure.persistence.repository.SpringDataAuditoriaRepository;
import com.odontomax.infrastructure.persistence.repository.SpringDataCajaMovimientoRepository;
import com.odontomax.infrastructure.persistence.repository.SpringDataCajaSesionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CajaEgresoModuloAdapter implements CajaEgresoModuloApi {
    private static final Set<String> METODOS_PAGO = Set.of("EFECTIVO", "YAPE", "PLIN", "TARJETA_DEBITO", "TARJETA_CREDITO");

    private final CajaService cajaService;
    private final SpringDataCajaMovimientoRepository cajaMovimientoRepository;
    private final SpringDataCajaSesionRepository cajaSesionRepository;
    private final SpringDataAuditoriaRepository auditoriaRepository;

    @Override
    @Transactional
    public UUID registrarEgresoDeSistema(
            String sedeId,
            BigDecimal monto,
            String metodoPago,
            String concepto,
            String referenciaTipo,
            UUID referenciaId
    ) {
        String metodo = validarOperacion(sedeId, monto, metodoPago, concepto, "del egreso");
        ClinicaUserPrincipal currentUser = SecurityUtils.getCurrentUser();

        CajaSesionEntity sesion = cajaService.obtenerOAbrirSesionAutomatica(sedeId);
        CajaMovimientoEntity movimiento = cajaMovimientoRepository.save(CajaMovimientoEntity.builder()
                .sesion(sesion)
                .tipo("EGRESO")
                .concepto(concepto)
                .monto(monto)
                .metodoPago(metodo)
                .referenciaTipo(referenciaTipo)
                .referenciaId(referenciaId)
                .registradoPor(currentUser.id())
                .build());

        auditoriaRepository.save(AuditoriaLogEntity.builder()
                .usuarioId(currentUser.id())
                .usuarioNombre(currentUser.nombre())
                .rol(currentUser.rol())
                .sedeId(sedeId)
                .modulo("CAJA")
                .accion("EGRESO_SISTEMA")
                .tabla("caja_movimientos")
                .registroId(movimiento.getId().toString())
                .detalle(String.format("Egreso de sistema por S/ %s (%s): %s", monto, metodo, concepto))
                .build());
        return sesion.getId();
    }

    @Override
    @Transactional
    public UUID revertirEgresoDeSistema(
            String sedeId,
            BigDecimal monto,
            String metodoPago,
            String concepto,
            String referenciaTipo,
            UUID referenciaId
    ) {
        String metodo = validarOperacion(sedeId, monto, metodoPago, concepto, "de la reversa");
        ClinicaUserPrincipal currentUser = SecurityUtils.getCurrentUser();

        Optional<CajaSesionEntity> sesionActiva = cajaSesionRepository
                .findBySedeIdAndAbiertaPorAndCerradaEnIsNull(sedeId, currentUser.id())
                .or(() -> cajaSesionRepository.findBySedeIdAndCerradaEnIsNull(sedeId));

        if (sesionActiva.isEmpty() && "EFECTIVO".equalsIgnoreCase(metodo)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "No se puede anular un pago en EFECTIVO porque no existe ninguna caja abierta actualmente en la sede para registrar el ingreso compensatorio."
            );
        }

        CajaSesionEntity sesionDestino = sesionActiva
                .orElseGet(() -> cajaService.obtenerOAbrirSesionAutomatica(sedeId));

        CajaMovimientoEntity movimiento = cajaMovimientoRepository.save(CajaMovimientoEntity.builder()
                .sesion(sesionDestino)
                .tipo("INGRESO")
                .concepto(concepto)
                .monto(monto)
                .metodoPago(metodo)
                .referenciaTipo(referenciaTipo)
                .referenciaId(referenciaId)
                .registradoPor(currentUser.id())
                .build());

        auditoriaRepository.save(AuditoriaLogEntity.builder()
                .usuarioId(currentUser.id())
                .usuarioNombre(currentUser.nombre())
                .rol(currentUser.rol())
                .sedeId(sedeId)
                .modulo("CAJA")
                .accion("REVERSA_EGRESO_SISTEMA")
                .tabla("caja_movimientos")
                .registroId(movimiento.getId().toString())
                .detalle(String.format("Reversa de egreso de sistema por S/ %s (%s): %s", monto, metodo, concepto))
                .build());

        return sesionDestino.getId();
    }

    private String validarOperacion(
            String sedeId,
            BigDecimal monto,
            String metodoPago,
            String concepto,
            String operacion
    ) {
        ClinicaUserPrincipal currentUser = SecurityUtils.getCurrentUser();
        if (currentUser == null) {
            throw new AccessDeniedException("No autenticado");
        }
        SecurityUtils.validarAccesoSede(sedeId);
        if (monto == null || monto.signum() <= 0) {
            throw new IllegalArgumentException(String.format("El monto %s debe ser mayor a cero.", operacion));
        }
        if (concepto == null || concepto.isBlank()) {
            throw new IllegalArgumentException(String.format("El concepto %s es obligatorio.", operacion));
        }

        String metodo = metodoPago != null ? metodoPago.trim().toUpperCase(Locale.ROOT) : "EFECTIVO";
        if (!METODOS_PAGO.contains(metodo)) {
            throw new IllegalArgumentException("El método de pago debe ser EFECTIVO, YAPE, PLIN, TARJETA_DEBITO o TARJETA_CREDITO");
        }
        return metodo;
    }
}
