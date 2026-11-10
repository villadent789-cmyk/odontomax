package com.odontomax.application.service;

import com.odontomax.application.dto.request.CajaAperturaRequestDTO;
import com.odontomax.application.dto.request.CajaCierreRequestDTO;
import com.odontomax.application.dto.request.CajaMovimientoRequestDTO;
import com.odontomax.application.dto.response.CajaMovimientoResponseDTO;
import com.odontomax.application.dto.response.CajaSesionResponseDTO;
import com.odontomax.infrastructure.persistence.entity.AuditoriaLogEntity;
import com.odontomax.infrastructure.persistence.entity.CajaMovimientoEntity;
import com.odontomax.infrastructure.persistence.entity.CajaSesionEntity;
import com.odontomax.infrastructure.persistence.repository.SpringDataAuditoriaRepository;
import com.odontomax.infrastructure.persistence.repository.SpringDataCajaMovimientoRepository;
import com.odontomax.infrastructure.persistence.repository.SpringDataCajaSesionRepository;
import com.odontomax.infrastructure.persistence.repository.SpringDataPagoRepository;
import com.odontomax.identidad.api.ClinicaUserPrincipal;
import com.odontomax.identidad.api.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CajaService {

    public static final ZoneId ZONE_LIMA = ZoneId.of("America/Lima");

    private final SpringDataCajaSesionRepository cajaSesionRepository;
    private final SpringDataCajaMovimientoRepository cajaMovimientoRepository;
    private final SpringDataPagoRepository pagoRepository;
    private final SpringDataAuditoriaRepository auditoriaRepository;

    private Clock clock = Clock.system(ZONE_LIMA);

    @org.springframework.beans.factory.annotation.Value("${caja.egreso.tope-movimiento:200.00}")
    private BigDecimal topeMovimiento = new BigDecimal("200.00");

    @org.springframework.beans.factory.annotation.Value("${caja.egreso.tope-diario-usuario:500.00}")
    private BigDecimal topeDiarioUsuario = new BigDecimal("500.00");

    @org.springframework.beans.factory.annotation.Value("${caja.egreso.tope-diario-sede:1000.00}")
    private BigDecimal topeDiarioSede = new BigDecimal("1000.00");

    public void setClock(Clock clock) {
        if (clock != null) {
            this.clock = clock;
        }
    }

    public Instant calcularInicioDiaLima() {
        return LocalDate.now(clock.withZone(ZONE_LIMA))
                .atStartOfDay(ZONE_LIMA)
                .toInstant();
    }

    public void setTopes(BigDecimal topeMovimiento, BigDecimal topeDiarioUsuario, BigDecimal topeDiarioSede) {
        if (topeMovimiento != null) this.topeMovimiento = topeMovimiento;
        if (topeDiarioUsuario != null) this.topeDiarioUsuario = topeDiarioUsuario;
        if (topeDiarioSede != null) this.topeDiarioSede = topeDiarioSede;
    }

    @Transactional(readOnly = true)
    public Optional<CajaSesionResponseDTO> obtenerSesionActiva(String sedeId) {
        SecurityUtils.validarAccesoSede(sedeId);
        ClinicaUserPrincipal currentUser = SecurityUtils.getCurrentUser();

        Optional<CajaSesionEntity> sesion = Optional.empty();
        if (currentUser != null) {
            sesion = cajaSesionRepository.findBySedeIdAndAbiertaPorAndCerradaEnIsNull(sedeId, currentUser.id());
        }
        if (sesion.isEmpty()) {
            sesion = cajaSesionRepository.findBySedeIdAndCerradaEnIsNull(sedeId);
        }

        boolean esOdontologo = currentUser != null && currentUser.soloOdontologo();
        return sesion.map(this::toResponseDTO).map(dto -> esOdontologo ? soloEstado(dto) : dto);
    }

    /** El odontólogo solo necesita saber si la caja está abierta: los totales y movimientos traen cobros y nombres de otros doctores. */
    private static CajaSesionResponseDTO soloEstado(CajaSesionResponseDTO dto) {
        return CajaSesionResponseDTO.builder().id(dto.getId()).sedeId(dto.getSedeId()).estado(dto.getEstado()).build();
    }

    @Transactional(readOnly = true)
    public List<CajaSesionResponseDTO> listarSesionesPorSede(String sedeId) {
        SecurityUtils.validarAccesoSede(sedeId);
        return cajaSesionRepository.findBySedeIdOrderByAbiertaEnDesc(sedeId)
                .stream()
                .map(this::toResponseDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public CajaSesionResponseDTO obtenerPorId(UUID id) {
        CajaSesionEntity sesion = cajaSesionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Sesión de caja no encontrada"));
        SecurityUtils.validarAccesoSede(sesion.getSedeId());
        return toResponseDTO(sesion);
    }

    @Transactional
    public CajaSesionResponseDTO abrirCaja(CajaAperturaRequestDTO dto) {
        ClinicaUserPrincipal currentUser = SecurityUtils.getCurrentUser();
        if (currentUser == null) {
            throw new AccessDeniedException("No autenticado");
        }

        SecurityUtils.validarAccesoSede(dto.getSedeId());

        // Validar que no exista un turno abierto por el mismo usuario y sede
        Optional<CajaSesionEntity> existenteUsuario = cajaSesionRepository
                .findBySedeIdAndAbiertaPorAndCerradaEnIsNull(dto.getSedeId(), currentUser.id());
        if (existenteUsuario.isPresent()) {
            throw new IllegalStateException("Ya mantiene una sesión de caja abierta en esta sede. Debe cerrarla antes de iniciar un nuevo turno.");
        }

        // Validar si la sede ya tiene una sesión abierta activa (según restricción única)
        Optional<CajaSesionEntity> existenteSede = cajaSesionRepository.findBySedeIdAndCerradaEnIsNull(dto.getSedeId());
        if (existenteSede.isPresent()) {
            throw new IllegalStateException("Ya existe una caja activa abierta para esta sede. Cierre la sesión anterior primero.");
        }

        BigDecimal montoApertura = BigDecimal.ZERO; // Se conserva el campo por compatibilidad; toda apertura inicia en cero.

        CajaSesionEntity sesion = CajaSesionEntity.builder()
                .sedeId(dto.getSedeId())
                .abiertaPor(currentUser.id())
                .montoApertura(montoApertura)
                .observaciones(dto.getObservaciones())
                .build();

        CajaSesionEntity guardada = cajaSesionRepository.save(sesion);

        // La caja inicia en cero; no se registra un movimiento de fondo.












        auditoriaRepository.save(AuditoriaLogEntity.builder()
                .usuarioId(currentUser.id())
                .usuarioNombre(currentUser.nombre())
                .rol(currentUser.rol())
                .sedeId(dto.getSedeId())
                .modulo("CAJA")
                .accion("APERTURA_CAJA")
                .tabla("caja_sesiones")
                .registroId(guardada.getId().toString())
                .detalle(String.format("Apertura de caja con fondo inicial de S/ %s", montoApertura))
                .build());

        return toResponseDTO(guardada);
    }

    /**
     * Sesión de caja abierta de la sede (prioriza la del usuario). Si no hay ninguna, la abre con
     * fondo S/ 0.00: no se exige una apertura manual previa para registrar cobros
     * o egresos, sea cual sea el método de pago.
     */
    @Transactional
    public CajaSesionEntity obtenerOAbrirSesionAutomatica(String sedeId) {
        ClinicaUserPrincipal currentUser = SecurityUtils.getCurrentUser();
        if (currentUser == null) {
            throw new AccessDeniedException("No autenticado");
        }
        Optional<CajaSesionEntity> abierta = cajaSesionRepository
                .findBySedeIdAndAbiertaPorAndCerradaEnIsNull(sedeId, currentUser.id())
                .or(() -> cajaSesionRepository.findBySedeIdAndCerradaEnIsNull(sedeId));
        if (abierta.isPresent()) {
            return abierta.get();
        }

        CajaSesionEntity guardada = cajaSesionRepository.save(CajaSesionEntity.builder()
                .sedeId(sedeId)
                .abiertaPor(currentUser.id())
                .montoApertura(BigDecimal.ZERO)
                .observaciones("Apertura automática con fondo S/ 0.00 al registrar el primer movimiento del periodo")
                .build());

        auditoriaRepository.save(AuditoriaLogEntity.builder()
                .usuarioId(currentUser.id())
                .usuarioNombre(currentUser.nombre())
                .rol(currentUser.rol())
                .sedeId(sedeId)
                .modulo("CAJA")
                .accion("APERTURA_CAJA_AUTOMATICA")
                .tabla("caja_sesiones")
                .registroId(guardada.getId().toString())
                .detalle("Apertura automática de caja con fondo S/ 0.00")
                .build());
        return guardada;
    }

    @Transactional
    public CajaMovimientoResponseDTO registrarMovimientoManual(UUID sesionId, CajaMovimientoRequestDTO dto) {
        ClinicaUserPrincipal currentUser = SecurityUtils.getCurrentUser();
        if (currentUser == null) {
            throw new AccessDeniedException("No autenticado");
        }

        CajaSesionEntity sesion = cajaSesionRepository.findById(sesionId)
                .orElseThrow(() -> new IllegalArgumentException("Sesión de caja no encontrada"));

        SecurityUtils.validarAccesoSede(sesion.getSedeId());

        if (sesion.getCerradaEn() != null) {
            throw new IllegalStateException("No se pueden registrar movimientos en una caja cerrada");
        }

        if ("EGRESO".equalsIgnoreCase(dto.getTipo())) {
            if (dto.getConcepto() == null || dto.getConcepto().trim().length() < 3) {
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.BAD_REQUEST,
                        "El motivo o concepto del egreso de caja es obligatorio."
                );
            }

            BigDecimal monto = dto.getMonto() != null ? dto.getMonto() : BigDecimal.ZERO;
            boolean esAdmin = "ADMIN_GENERAL".equals(currentUser.rol()) || "ADMIN_SEDE".equals(currentUser.rol());

            // 1. Tope por movimiento individual (configurable por variable de entorno)
            if (monto.compareTo(topeMovimiento) > 0 && !esAdmin) {
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.FORBIDDEN,
                        String.format("Los egresos manuales de caja mayores a S/ %s requieren autorización de un Administrador de Sede o Administrador General.", topeMovimiento)
                );
            }

            // 2. Tope diario por usuario y sede (configurable por variable de entorno, zona horaria America/Lima)
            Instant inicioDia = calcularInicioDiaLima();
            BigDecimal acumuladoUsuario = cajaMovimientoRepository.sumEgresosManualesUsuarioYDia(currentUser.id(), sesion.getSedeId(), inicioDia);
            if (acumuladoUsuario.add(monto).compareTo(topeDiarioUsuario) > 0 && !esAdmin) {
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.FORBIDDEN,
                        String.format("Ha superado el tope diario permitido de egresos manuales por usuario (S/ %s). Requiere autorización de Administrador de Sede o Administrador General.", topeDiarioUsuario)
                );
            }

            // 3. Tope diario global de la sede (configurable por variable de entorno)
            BigDecimal acumuladoSede = cajaMovimientoRepository.sumEgresosManualesSedeYDia(sesion.getSedeId(), inicioDia);
            if (acumuladoSede.add(monto).compareTo(topeDiarioSede) > 0 && !"ADMIN_GENERAL".equals(currentUser.rol())) {
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.FORBIDDEN,
                        String.format("Se ha superado el tope diario permitido de egresos manuales para la sede (S/ %s). Requiere autorización de Administrador General.", topeDiarioSede)
                );
            }
        }

        CajaMovimientoEntity mov = CajaMovimientoEntity.builder()
                .sesion(sesion)
                .tipo(validarTipoMovimiento(dto.getTipo()))
                .concepto(dto.getConcepto())
                .monto(dto.getMonto())
                .metodoPago(validarMetodoPago(dto.getMetodoPago()))
                .referenciaTipo(dto.getReferenciaTipo() != null ? dto.getReferenciaTipo() : "AJUSTE_MANUAL")
                .referenciaId(dto.getReferenciaId())
                .registradoPor(currentUser.id())
                .build();

        CajaMovimientoEntity guardado = cajaMovimientoRepository.save(mov);

        auditoriaRepository.save(AuditoriaLogEntity.builder()
                .usuarioId(currentUser.id())
                .usuarioNombre(currentUser.nombre())
                .rol(currentUser.rol())
                .sedeId(sesion.getSedeId())
                .modulo("CAJA")
                .accion("MOVIMIENTO_MANUAL")
                .tabla("caja_movimientos")
                .registroId(guardado.getId().toString())
                .detalle(String.format("Movimiento %s de S/ %s: %s", mov.getTipo(), mov.getMonto(), mov.getConcepto()))
                .build());

        return toMovimientoDTO(guardado);
    }

    @Transactional
    public CajaSesionResponseDTO cerrarCaja(UUID sesionId, CajaCierreRequestDTO dto) {
        ClinicaUserPrincipal currentUser = SecurityUtils.getCurrentUser();
        if (currentUser == null) {
            throw new AccessDeniedException("No autenticado");
        }

        CajaSesionEntity sesion = cajaSesionRepository.findById(sesionId)
                .orElseThrow(() -> new IllegalArgumentException("Sesión de caja no encontrada"));

        SecurityUtils.validarAccesoSede(sesion.getSedeId());

        if (sesion.getCerradaEn() != null) {
            throw new IllegalStateException("Esta sesión de caja ya fue cerrada");
        }

        boolean esQuienAbrio = currentUser.id().equals(sesion.getAbiertaPor());
        boolean esAdmin = "ADMIN_GENERAL".equals(currentUser.rol()) || "ADMIN_SEDE".equals(currentUser.rol());
        // Sedes sin cajero: el odontólogo cuadra y cierra la caja de SU sede (validarAccesoSede ya lo
        // restringe a su sede), aunque la haya abierto otro doctor o la apertura automática.
        boolean esOdontologoDeLaSede = currentUser.tieneRol("ODONTOLOGO");
        if (!esQuienAbrio && !esAdmin && !esOdontologoDeLaSede) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN,
                    "Únicamente el usuario que abrió el turno de caja, un odontólogo de la sede o un Administrador pueden cerrarla."
            );
        }

        com.odontomax.application.port.out.CajaTotalesSesion totales = cajaPeriodoPort.calcularTotales(sesionId);
        BigDecimal montoEsperado = totales.montoEsperadoEfectivo();
        BigDecimal montoEsperadoYape = totales.ingresosYape().subtract(totales.egresosYape());
        BigDecimal montoEsperadoPlin = totales.ingresosPlin().subtract(totales.egresosPlin());
        BigDecimal montoEsperadoTarjeta = totales.ingresosTarjetaDebito().add(totales.ingresosTarjetaCredito())
                .subtract(totales.egresosTarjeta());
        BigDecimal montoContado = dto.getMontoContado();
        BigDecimal montoContadoYape = dto.getMontoContadoYape() != null ? dto.getMontoContadoYape() : BigDecimal.ZERO;
        BigDecimal montoContadoPlin = dto.getMontoContadoPlin() != null ? dto.getMontoContadoPlin() : BigDecimal.ZERO;
        BigDecimal montoContadoTarjeta = dto.getMontoContadoTarjeta() != null ? dto.getMontoContadoTarjeta() : BigDecimal.ZERO;
        BigDecimal diferencia = montoContado.subtract(montoEsperado);
        BigDecimal diferenciaYape = montoContadoYape.subtract(montoEsperadoYape);
        BigDecimal diferenciaPlin = montoContadoPlin.subtract(montoEsperadoPlin);
        BigDecimal diferenciaTarjeta = montoContadoTarjeta.subtract(montoEsperadoTarjeta);
        if ((diferencia.signum() != 0 || diferenciaYape.signum() != 0 || diferenciaPlin.signum() != 0
                || diferenciaTarjeta.signum() != 0)
                && (dto.getObservaciones() == null || dto.getObservaciones().isBlank())) {
            throw new com.odontomax.domain.exception.DiferenciaCajaSinObservacionesException();
        }
        cajaPeriodoPort.guardarCierreMetodos(sesionId, montoEsperadoYape, montoContadoYape, montoEsperadoPlin, montoContadoPlin,
                montoEsperadoTarjeta, montoContadoTarjeta);





        sesion.setMontoEsperado(montoEsperado);
        sesion.setMontoContado(montoContado);
        sesion.setCerradaEn(Instant.now());
        sesion.setCerradaPor(currentUser.id());
        if (dto.getObservaciones() != null) {
            sesion.setObservaciones(dto.getObservaciones());
        }

        CajaSesionEntity cerrada = cajaSesionRepository.save(sesion);

        auditoriaRepository.save(AuditoriaLogEntity.builder()
                .usuarioId(currentUser.id())
                .usuarioNombre(currentUser.nombre())
                .rol(currentUser.rol())
                .sedeId(sesion.getSedeId())
                .modulo("CAJA")
                .accion("CIERRE_CAJA")
                .tabla("caja_sesiones")
                .registroId(cerrada.getId().toString())
                .detalle(String.format("Cierre de caja por método. Efectivo: esperado S/ %s, contado S/ %s, diferencia S/ %s; Yape: esperado S/ %s, contado S/ %s, diferencia S/ %s; Plin: esperado S/ %s, contado S/ %s, diferencia S/ %s; Tarjetas: esperado S/ %s, contado S/ %s, diferencia S/ %s",
                        montoEsperado, montoContado, diferencia, montoEsperadoYape, montoContadoYape, diferenciaYape, montoEsperadoPlin, montoContadoPlin, diferenciaPlin,
                        montoEsperadoTarjeta, montoContadoTarjeta, diferenciaTarjeta))
                .build());

        return toResponseDTO(cerrada);
    }

    private CajaSesionResponseDTO toResponseDTO(CajaSesionEntity s) {
        List<CajaMovimientoEntity> movs = cajaMovimientoRepository.findBySesionIdOrderByRegistradoEnAsc(s.getId());

        com.odontomax.application.port.out.CajaTotalesSesion totales = cajaPeriodoPort.calcularTotales(s.getId());
        BigDecimal ingresosEfectivo = totales.ingresosEfectivo();
        BigDecimal egresosEfectivo = totales.egresosEfectivo();
        BigDecimal totalYape = totales.ingresosYape();
        BigDecimal esperadoYape = totales.montoEsperadoYape();
        BigDecimal esperadoPlin = totales.montoEsperadoPlin();
        BigDecimal montoContadoYape = totales.montoContadoYape();
        BigDecimal montoContadoPlin = totales.montoContadoPlin();
        BigDecimal diferenciaYape = totales.diferenciaYape();
        BigDecimal diferenciaPlin = totales.diferenciaPlin();
        BigDecimal ingresosPlin = totales.ingresosPlin();
        BigDecimal egresosPlin = totales.egresosPlin();
        BigDecimal ingresosYape = totales.ingresosYape();
        BigDecimal egresosYape = totales.egresosYape();








        BigDecimal esperado = s.getMontoEsperado();
        if (esperado == null) {
            esperado = ingresosEfectivo.subtract(egresosEfectivo);
        }

        BigDecimal diferencia = s.getDiferencia();
        if (diferencia == null && s.getMontoContado() != null) {
            diferencia = s.getMontoContado().subtract(esperado);
        }

        List<CajaMovimientoResponseDTO> movsDTO = movs.stream().map(this::toMovimientoDTO).toList();

        return completarResponse(CajaSesionResponseDTO.builder()
                .id(s.getId())
                .sedeId(s.getSedeId())
                .sedeNombre(s.getSedeId())
                .abiertaPor(s.getAbiertaPor())
                .abiertaEn(s.getAbiertaEn())
                .montoApertura(s.getMontoApertura())
                .cerradaPor(s.getCerradaPor())
                .cerradaEn(s.getCerradaEn())
                .montoEsperado(esperado)
                .montoContado(s.getMontoContado())
                .diferencia(diferencia)
                .totalIngresosEfectivo(ingresosEfectivo)
                .totalEgresosEfectivo(egresosEfectivo)
                .totalYape(totalYape)
                .estado(s.getCerradaEn() == null ? "ABIERTA" : "CERRADA")
                .observaciones(s.getObservaciones())
                .movimientos(movsDTO)
                .build(), totales);
    }

    private CajaMovimientoResponseDTO toMovimientoDTO(CajaMovimientoEntity m) {
        return CajaMovimientoResponseDTO.builder()
                .id(m.getId())
                .sesionId(m.getSesion() != null ? m.getSesion().getId() : null)
                .tipo(m.getTipo())
                .concepto(m.getConcepto())
                .monto(m.getMonto())
                .metodoPago(m.getMetodoPago())
                .referenciaTipo(m.getReferenciaTipo())
                .referenciaId(m.getReferenciaId())
                .registradoPor(m.getRegistradoPor())
                .registradoEn(m.getRegistradoEn())
                .build();
    }

    private CajaSesionResponseDTO completarResponse(CajaSesionResponseDTO dto,
            com.odontomax.application.port.out.CajaTotalesSesion totales) {
        dto.setMontoEsperadoYape(totales.montoEsperadoYape());
        dto.setMontoContadoYape(totales.montoContadoYape());
        dto.setDiferenciaYape(totales.diferenciaYape());
        dto.setMontoEsperadoPlin(totales.montoEsperadoPlin());
        dto.setMontoContadoPlin(totales.montoContadoPlin());
        dto.setDiferenciaPlin(totales.diferenciaPlin());
        dto.setTotalIngresosYape(totales.ingresosYape());
        dto.setTotalEgresosYape(totales.egresosYape());
        dto.setTotalIngresosPlin(totales.ingresosPlin());
        dto.setTotalEgresosPlin(totales.egresosPlin());
        dto.setMontoEsperadoTarjeta(totales.montoEsperadoTarjeta());
        dto.setMontoContadoTarjeta(totales.montoContadoTarjeta());
        dto.setDiferenciaTarjeta(totales.diferenciaTarjeta());
        dto.setTotalIngresosTarjetaDebito(totales.ingresosTarjetaDebito());
        dto.setTotalIngresosTarjetaCredito(totales.ingresosTarjetaCredito());
        dto.setTotalEgresosTarjeta(totales.egresosTarjeta());
        dto.setAbiertaPorNombre(nombreUsuario(dto.getAbiertaPor()));
        dto.setCerradaPorNombre(nombreUsuario(dto.getCerradaPor()));
        return dto;
    }

    @Transactional(readOnly = true)
    public com.odontomax.application.dto.response.CajaResumenDiarioDTO obtenerResumenDiario(String sedeId, LocalDate fecha) {
        SecurityUtils.validarAccesoSede(sedeId);
        LocalDate dia = fecha != null ? fecha : LocalDate.now(clock.withZone(ZONE_LIMA));
        Instant desde = dia.atStartOfDay(ZONE_LIMA).toInstant();
        Instant hasta = dia.plusDays(1).atStartOfDay(ZONE_LIMA).toInstant();
        return cajaPeriodoPort.obtenerResumenDiario(sedeId, desde, hasta);
    }

    private String nombreUsuario(UUID usuarioId) {
        if (usuarioId == null) {
            return null;
        }
        return consultaUsuarioModuloApi.buscarPorId(usuarioId)
                .map(com.odontomax.identidad.api.UsuarioModuloDTO::nombre)
                .orElse(usuarioId.toString());
    }

    private static String validarMetodoPago(String metodoPago) {
        String metodo = metodoPago != null ? metodoPago.toUpperCase() : "EFECTIVO";
        if (!List.of("EFECTIVO", "YAPE", "PLIN", "TARJETA_DEBITO", "TARJETA_CREDITO").contains(metodo)) {
            throw new IllegalArgumentException("El método de pago debe ser EFECTIVO, YAPE, PLIN, TARJETA_DEBITO o TARJETA_CREDITO");
        }
        return metodo;
    }

    private static String validarTipoMovimiento(String tipo) {
        if ("INGRESO".equalsIgnoreCase(tipo)) {
            throw new com.odontomax.domain.exception.IngresoManualCajaNoPermitidoException();
        }
        return tipo.toUpperCase();
    }

    @org.springframework.beans.factory.annotation.Autowired
    public void setCajaPeriodoPort(com.odontomax.application.port.out.CajaPeriodoPort cajaPeriodoPort) {
        this.cajaPeriodoPort = cajaPeriodoPort;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public void setConsultaUsuarioModuloApi(com.odontomax.identidad.api.ConsultaUsuarioModuloApi consultaUsuarioModuloApi) {
        this.consultaUsuarioModuloApi = consultaUsuarioModuloApi;
    }

    private com.odontomax.application.port.out.CajaPeriodoPort cajaPeriodoPort;
    private com.odontomax.identidad.api.ConsultaUsuarioModuloApi consultaUsuarioModuloApi;
}
