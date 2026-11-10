package com.odontomax.tratamientos.service;

import java.util.Set;
import com.odontomax.application.dto.request.TratamientoAceptacionRequestDTO;
import com.odontomax.application.dto.request.TratamientoCreateRequestDTO;
import com.odontomax.application.dto.request.TratamientoItemRequestDTO;
import com.odontomax.application.dto.request.TratamientoUpdateRequestDTO;
import com.odontomax.application.dto.response.TratamientoItemResponseDTO;
import com.odontomax.application.dto.response.CambioSesionesResponseDTO;
import com.odontomax.application.dto.response.TratamientoResponseDTO;
import com.odontomax.infrastructure.persistence.entity.*;
import com.odontomax.infrastructure.persistence.repository.*;
import com.odontomax.agenda.api.ConsultaEspecialistasModuloApi;
import com.odontomax.identidad.api.ClinicaUserPrincipal;
import com.odontomax.identidad.api.SecurityUtils;
import com.odontomax.pacientes.api.ConsultaPacientesModuloApi;
import com.odontomax.pacientes.api.PacienteResumen;
import com.odontomax.tratamientos.api.TratamientoCobroModuloApi;
import com.odontomax.tratamientos.domain.model.CronogramaCuotas;
import com.odontomax.application.dto.response.CuotaPlanResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.odontomax.domain.exception.PagoExcedeSaldoTratamientoException;

@Service
@RequiredArgsConstructor
public class TratamientoService implements TratamientoCobroModuloApi, com.odontomax.tratamientos.api.CuotasVencidasModuloApi {

    private final SpringDataTratamientoRepository tratamientoRepository;
    private final SpringDataTratamientoItemRepository itemRepository;
    private final SpringDataPlanCuotaRepository cuotaRepository;
    private final SpringDataPlanSesionCambioRepository sesionCambioRepository;
    private final ConsultaPacientesModuloApi pacientesApi;
    private final SpringDataPagoRepository pagoRepository;
    private final SpringDataAuditoriaRepository auditoriaRepository;
    private final ConsultaEspecialistasModuloApi consultaEspecialistasApi;
    private com.odontomax.sedes.api.ConsultaSedesModuloApi sedesApi;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setSedesApi(com.odontomax.sedes.api.ConsultaSedesModuloApi sedesApi) {
        this.sedesApi = sedesApi;
    }

    @Transactional(readOnly = true)
    public List<TratamientoResponseDTO> listarPorPaciente(UUID pacienteId) {
        PacienteResumen paciente = pacientesApi.findById(pacienteId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el paciente con ID: " + pacienteId));

        SecurityUtils.validarAccesoSede(paciente.sedeId());
        consultaEspecialistasApi.exigirAccesoDeOdontologo(pacienteId);

        return tratamientoRepository.findByPacienteIdOrderByCreadoEnDesc(pacienteId)
                .stream()
                .map(this::toResponseDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TratamientoResponseDTO> listarPorSede(String sedeId) {
        SecurityUtils.validarAccesoSede(sedeId);
        ClinicaUserPrincipal usuario = SecurityUtils.getCurrentUser();
        boolean esOdontologo = usuario != null && usuario.soloOdontologo();
        Set<UUID> pacientesDoctor = (esOdontologo && usuario != null)
                ? consultaEspecialistasApi.pacientesDelOdontologo(usuario.id())
                : Set.of();
        return tratamientoRepository.findBySedeIdOrderByCreadoEnDesc(sedeId)
                .stream()
                .filter(t -> !esOdontologo || pacientesDoctor.contains(t.getPacienteId()))
                .map(this::toResponseDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public TratamientoResponseDTO obtenerPorId(UUID id) {
        TratamientoEntity entity = tratamientoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Tratamiento no encontrado con ID: " + id));

        SecurityUtils.validarAccesoSede(entity.getSedeId());
        consultaEspecialistasApi.exigirAccesoDeOdontologo(entity.getPacienteId());
        return toResponseDTO(entity);
    }

    @Transactional
    public TratamientoResponseDTO crear(TratamientoCreateRequestDTO dto) {
        ClinicaUserPrincipal currentUser = SecurityUtils.getCurrentUser();
        if (currentUser == null) {
            throw new AccessDeniedException("No autenticado");
        }

        SecurityUtils.validarAccesoSede(dto.getSedeId());

        PacienteResumen paciente = pacientesApi.findById(dto.getPacienteId())
                .orElseThrow(() -> new IllegalArgumentException("Paciente no encontrado con ID: " + dto.getPacienteId()));

        SecurityUtils.validarAccesoSede(paciente.sedeId());
        consultaEspecialistasApi.exigirAccesoDeOdontologo(dto.getPacienteId());

        // 1. Calcular subtotal de items o costo base
        BigDecimal subtotalBase = BigDecimal.ZERO;
        BigDecimal descuentoTotalItems = BigDecimal.ZERO;
        List<TratamientoItemEntity> itemsAInsertar = new ArrayList<>();

        if (dto.getItems() != null && !dto.getItems().isEmpty()) {
            for (TratamientoItemRequestDTO itemDTO : dto.getItems()) {
                BigDecimal precioBase = itemDTO.getPrecioBase() != null ? itemDTO.getPrecioBase() : BigDecimal.ZERO;
                int cant = itemDTO.getCantidad() != null && itemDTO.getCantidad() > 0 ? itemDTO.getCantidad() : 1;
                BigDecimal descItem = itemDTO.getDescuento() != null ? itemDTO.getDescuento() : BigDecimal.ZERO;

                BigDecimal subtotalItem = precioBase.multiply(BigDecimal.valueOf(cant));
                subtotalBase = subtotalBase.add(subtotalItem);
                descuentoTotalItems = descuentoTotalItems.add(descItem);

                BigDecimal precioFinal = subtotalItem.subtract(descItem);
                if (precioFinal.compareTo(BigDecimal.ZERO) < 0) {
                    precioFinal = BigDecimal.ZERO;
                }

                itemsAInsertar.add(TratamientoItemEntity.builder()
                        .procedimientoId(itemDTO.getProcedimientoId())
                        .nombreProcedimiento(itemDTO.getNombreProcedimiento())
                        .piezaFdi(itemDTO.getPiezaFdi())
                        .precioBase(precioBase)
                        .cantidad(cant)
                        .descuento(descItem)
                        .descuentoAutorizadoPor(descItem.compareTo(BigDecimal.ZERO) > 0 ? currentUser.id() : null)
                        .precioFinal(precioFinal)
                        .estado("PLANIFICADO")
                        .observaciones(itemDTO.getObservaciones())
                        .gravedad(itemDTO.getGravedad())
                        .build());
            }
        } else {
            subtotalBase = dto.getCostoTotal() != null ? dto.getCostoTotal() : BigDecimal.ZERO;
        }

        // Plan en cuotas: el costo es la suma del cronograma, que no admite descuento aparte (se ajusta la cuota).
        List<CronogramaCuotas.Cuota> cronograma = null;
        if (dto.getCuotas() != null) {
            var c = dto.getCuotas();
            cronograma = CronogramaCuotas.generar(c.getMontoInicial(), c.getMontoMensual(), c.getCantidadMensuales(), c.getPrimerVencimiento());
            subtotalBase = CronogramaCuotas.total(cronograma);
            descuentoTotalItems = BigDecimal.ZERO;
            if (dto.getDescuentoTotal() != null && dto.getDescuentoTotal().signum() > 0) {
                throw new IllegalArgumentException("Un plan en cuotas no admite descuento: ajusta el monto de las cuotas.");
            }
        }

        BigDecimal descuentoFinal = dto.getDescuentoTotal() != null && dto.getDescuentoTotal().compareTo(BigDecimal.ZERO) > 0
                ? dto.getDescuentoTotal()
                : descuentoTotalItems;

        // 2. Validación de topes de descuento por rol (ODONTOLOGO: 10%, ADMIN_SEDE: 30%, mayor ADMIN_GENERAL)
        if (descuentoFinal.compareTo(BigDecimal.ZERO) > 0 && subtotalBase.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal porcentajeDescuento = descuentoFinal
                    .multiply(BigDecimal.valueOf(100))
                    .divide(subtotalBase, 2, java.math.RoundingMode.HALF_UP);

            // Con funciones sumadas rige el tope más alto: administración, luego odontólogo, luego recepción
            String rol = currentUser.tieneRol("ADMIN_SEDE") ? "ADMIN_SEDE"
                    : currentUser.tieneRol("ODONTOLOGO") && !currentUser.tieneRol("ADMIN_GENERAL") ? "ODONTOLOGO" : currentUser.rol();
            if ("RECEPCION".equalsIgnoreCase(rol)) {
                if (!"VILLAMAX".equalsIgnoreCase(SecurityUtils.negocioDelCatalogo())) {
                    throw new org.springframework.web.server.ResponseStatusException(
                            org.springframework.http.HttpStatus.FORBIDDEN,
                            "El rol RECEPCION no tiene autorización para otorgar descuentos en presupuestos odontológicos."
                    );
                }
            } else if ("ODONTOLOGO".equalsIgnoreCase(rol)) {
                if (porcentajeDescuento.compareTo(new BigDecimal("10.00")) > 0) {
                    throw new org.springframework.web.server.ResponseStatusException(
                            org.springframework.http.HttpStatus.FORBIDDEN,
                            String.format("El descuento del %s%% excede el límite máximo permitido para su rol de ODONTOLOGO (10.0%%). Requiere autorización de Administrador de Sede o Administrador General.", porcentajeDescuento)
                    );
                }
            } else if ("ADMIN_SEDE".equalsIgnoreCase(rol)) {
                if (porcentajeDescuento.compareTo(new BigDecimal("30.00")) > 0) {
                    throw new org.springframework.web.server.ResponseStatusException(
                            org.springframework.http.HttpStatus.FORBIDDEN,
                            String.format("El descuento del %s%% excede el límite máximo permitido para Administrador de Sede (30.0%%). Descuentos mayores requieren autorización de Administrador General.", porcentajeDescuento)
                    );
                }
            }
        }

        // Precio acordado: si difiere del catálogo, queda constancia de quién y por qué (solo el personal clínico y los administradores).
        BigDecimal precioCatalogo = cronograma == null ? dto.getPrecioCatalogo() : null;
        String motivoAjuste = null;
        if (precioCatalogo != null && precioCatalogo.compareTo(subtotalBase) != 0) {
            if (currentUser.soloRecepcion()) {
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.FORBIDDEN,
                        "Solo el odontólogo o un administrador pueden acordar un precio distinto al del catálogo.");
            }
            motivoAjuste = dto.getMotivoAjustePrecio() != null ? dto.getMotivoAjustePrecio().trim() : "";
            if (motivoAjuste.length() < 5) {
                throw new IllegalArgumentException("Indica el motivo del precio acordado (mínimo 5 caracteres).");
            }
        }

        // 3. Cálculo estricto del costo total en el servidor (fuente de verdad financiera)
        BigDecimal costoTotalCalculado = subtotalBase.subtract(descuentoFinal);
        if (costoTotalCalculado.compareTo(BigDecimal.ZERO) < 0) {
            costoTotalCalculado = BigDecimal.ZERO;
        }

        UUID descuentoAutorizadoPor = (descuentoFinal.compareTo(BigDecimal.ZERO) > 0) ? currentUser.id() : null;

        BigDecimal pagadoAntesSistema = dto.getPagadoAntesSistema() != null
                && dto.getPagadoAntesSistema().compareTo(BigDecimal.ZERO) > 0
                ? dto.getPagadoAntesSistema()
                : BigDecimal.ZERO;

        TratamientoEntity entity = TratamientoEntity.builder()
                .pacienteId(paciente.id())
                .sedeId(dto.getSedeId())
                .especialistaId(dto.getEspecialistaId())
                .procedimientoId(dto.getProcedimientoId())
                .nombre(dto.getNombre())
                .piezaDental(dto.getPiezaDental())
                .diagnostico(dto.getDiagnostico())
                .modalidadPago(cronograma != null ? "CUOTAS" : (dto.getModalidadPago() != null ? dto.getModalidadPago() : "CONTADO"))
                .costoTotal(costoTotalCalculado)
                .descuentoTotal(descuentoFinal)
                .descuentoAutorizadoPor(descuentoAutorizadoPor)
                // Sin etapa de "aprobar presupuesto": registrar el tratamiento es iniciarlo
                .estado("EN_PROCESO")
                .aceptadoPorPaciente(true)
                .fechaAceptacion(java.time.Instant.now())
                .fechaInicio(dto.getFechaInicio())
                .fechaFinEstimada(dto.getFechaFinEstimada())
                .totalSesiones(dto.getTotalSesiones() != null ? dto.getTotalSesiones() : 1)
                .sesionesRealizadas(0)
                .observaciones(dto.getObservaciones())
                .pagadoAntesSistema(pagadoAntesSistema)
                .precioCatalogo(motivoAjuste != null ? precioCatalogo : null)
                .motivoAjustePrecio(motivoAjuste)
                .ajustePrecioPor(motivoAjuste != null ? currentUser.id() : null)
                .items(new ArrayList<>())
                .build();

        TratamientoEntity guardado = tratamientoRepository.save(entity);

        if (cronograma != null) {
            for (CronogramaCuotas.Cuota c : cronograma) {
                cuotaRepository.save(PlanCuotaEntity.builder()
                        .tratamientoId(guardado.getId())
                        .numero(c.numero())
                        .concepto(c.concepto())
                        .venceEn(c.venceEn())
                        .monto(c.monto())
                        .build());
            }
        }

        for (TratamientoItemEntity item : itemsAInsertar) {
            item.setTratamiento(guardado);
            itemRepository.save(item);
            guardado.getItems().add(item);
        }

        auditoriaRepository.save(AuditoriaLogEntity.builder()
                .usuarioId(currentUser.id())
                .usuarioNombre(currentUser.nombre())
                .rol(currentUser.rol())
                .sedeId(dto.getSedeId())
                .modulo("TRATAMIENTOS")
                .accion("CREAR_PLAN")
                .tabla("tratamientos")
                .registroId(guardado.getId().toString())
                .detalle(String.format("Plan de tratamiento '%s' registrado para paciente %s por S/ %s",
                        guardado.getNombre(), nombreCompleto(paciente), guardado.getCostoTotal()))
                .build());

        return toResponseDTO(guardado);
    }

    @Transactional
    public TratamientoResponseDTO actualizar(UUID id, TratamientoUpdateRequestDTO dto) {
        ClinicaUserPrincipal currentUser = SecurityUtils.getCurrentUser();
        TratamientoEntity entity = tratamientoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Tratamiento no encontrado"));

        SecurityUtils.validarAccesoSede(entity.getSedeId());
        consultaEspecialistasApi.exigirAccesoDeOdontologo(entity.getPacienteId());

        if (currentUser != null && currentUser.soloOdontologo()) {
            Optional<UUID> propio = consultaEspecialistasApi.findEspecialistaIdByUsuarioId(currentUser.id());
            if (propio.isPresent() && entity.getEspecialistaId() != null && !entity.getEspecialistaId().equals(propio.get())) {
                throw new AccessDeniedException("Solo el odontólogo responsable del plan o la administración pueden modificar o cerrar este tratamiento.");
            }
        }

        com.odontomax.tratamientos.domain.model.Tratamiento model =
                new com.odontomax.tratamientos.domain.model.Tratamiento(
                        entity.getId(),
                        entity.getPacienteId(),
                        entity.getSedeId(),
                        entity.getEspecialistaId(),
                        entity.getNombre(),
                        entity.getEstado(),
                        entity.getTotalSesiones(),
                        entity.getSesionesRealizadas(),
                        entity.getCostoTotal(),
                        entity.getMotivoCierre(),
                        entity.getCerradoEn()
                );

        if (dto.getEstado() != null) {
            model.cambiarEstado(dto.getEstado(), dto.getMotivoCierre());
            entity.setEstado(model.getEstado());
            entity.setMotivoCierre(model.getMotivoCierre());
            entity.setCerradoEn(model.getCerradoEn());
        }
        if (dto.getSesionesRealizadas() != null) {
            if (model.esEstadoFinal() && !dto.getSesionesRealizadas().equals(entity.getSesionesRealizadas())) {
                throw new IllegalStateException(
                        String.format("No se pueden registrar sesiones en un tratamiento en estado final '%s'", entity.getEstado())
                );
            }
            entity.setSesionesRealizadas(dto.getSesionesRealizadas());
        }
        if (dto.getTotalSesiones() != null && !dto.getTotalSesiones().equals(entity.getTotalSesiones())) {
            if (model.esEstadoFinal()) {
                throw new IllegalStateException(
                        String.format("No se puede cambiar el número de sesiones de un tratamiento en estado final '%s'", entity.getEstado()));
            }
            String motivoSesiones = dto.getMotivoCambioSesiones() != null ? dto.getMotivoCambioSesiones().trim() : "";
            if (motivoSesiones.length() < 5) {
                throw new IllegalArgumentException("Indica el motivo del cambio de sesiones (mínimo 5 caracteres).");
            }
            int realizadas = entity.getSesionesRealizadas() != null ? entity.getSesionesRealizadas() : 0;
            if (dto.getTotalSesiones() < Math.max(1, realizadas)) {
                throw new IllegalArgumentException(
                        String.format("El plan no puede tener menos sesiones que las ya realizadas (%d).", realizadas));
            }
            sesionCambioRepository.save(PlanSesionCambioEntity.builder()
                    .tratamientoId(id)
                    .sesionesAntes(entity.getTotalSesiones() != null ? entity.getTotalSesiones() : 1)
                    .sesionesDespues(dto.getTotalSesiones())
                    .motivo(motivoSesiones)
                    .usuarioId(currentUser != null ? currentUser.id() : null)
                    .usuarioNombre(currentUser != null ? currentUser.nombre() : "Sistema")
                    .build());
            auditoriaRepository.save(AuditoriaLogEntity.builder()
                    .usuarioId(currentUser != null ? currentUser.id() : null)
                    .usuarioNombre(currentUser != null ? currentUser.nombre() : "Sistema")
                    .rol(currentUser != null ? currentUser.rol() : "SISTEMA")
                    .sedeId(entity.getSedeId())
                    .modulo("TRATAMIENTOS")
                    .accion("CAMBIAR_SESIONES_PLAN")
                    .tabla("tratamientos")
                    .registroId(id.toString())
                    .detalle(String.format("Sesiones de '%s': de %d a %d. Motivo: %s",
                            entity.getNombre(), entity.getTotalSesiones(), dto.getTotalSesiones(), motivoSesiones))
                    .build());
            entity.setTotalSesiones(dto.getTotalSesiones());
        }
        if (dto.getProximoControl() != null) {
            entity.setProximoControl(dto.getProximoControl());
        }
        if (dto.getRequiereSeguimiento() != null) {
            entity.setRequiereSeguimiento(dto.getRequiereSeguimiento());
        }
        if (dto.getObservaciones() != null) {
            entity.setObservaciones(dto.getObservaciones());
        }

        TratamientoEntity actualizado = tratamientoRepository.save(entity);

        auditoriaRepository.save(AuditoriaLogEntity.builder()
                .usuarioId(currentUser != null ? currentUser.id() : null)
                .usuarioNombre(currentUser != null ? currentUser.nombre() : "Sistema")
                .rol(currentUser != null ? currentUser.rol() : "SISTEMA")
                .sedeId(entity.getSedeId())
                .modulo("TRATAMIENTOS")
                .accion("ACTUALIZAR_ESTADO")
                .tabla("tratamientos")
                .registroId(id.toString())
                .detalle(String.format("Tratamiento '%s' actualizado a estado '%s'", entity.getNombre(), entity.getEstado()))
                .build());

        return toResponseDTO(actualizado);
    }

    @Transactional
    public TratamientoResponseDTO aceptarPresupuesto(UUID id, TratamientoAceptacionRequestDTO dto) {
        ClinicaUserPrincipal currentUser = SecurityUtils.getCurrentUser();
        TratamientoEntity entity = tratamientoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Tratamiento no encontrado"));

        SecurityUtils.validarAccesoSede(entity.getSedeId());
        consultaEspecialistasApi.exigirAccesoDeOdontologo(entity.getPacienteId());

        entity.setAceptadoPorPaciente(dto.getAceptado());
        entity.setFechaAceptacion(dto.getAceptado() ? Instant.now() : null);
        if (dto.getAceptado() && "PLANIFICADO".equalsIgnoreCase(entity.getEstado())) {
            entity.setEstado("EN_PROCESO");
        }

        TratamientoEntity actualizado = tratamientoRepository.save(entity);

        auditoriaRepository.save(AuditoriaLogEntity.builder()
                .usuarioId(currentUser != null ? currentUser.id() : null)
                .usuarioNombre(currentUser != null ? currentUser.nombre() : "Sistema")
                .rol(currentUser != null ? currentUser.rol() : "SISTEMA")
                .sedeId(entity.getSedeId())
                .modulo("TRATAMIENTOS")
                .accion("ACEPTACION_PRESUPUESTO")
                .tabla("tratamientos")
                .registroId(id.toString())
                .detalle(String.format("Presupuesto para '%s' marcado como %s por el paciente",
                        entity.getNombre(), dto.getAceptado() ? "ACEPTADO" : "RECHAZADO"))
                .build());

        return toResponseDTO(actualizado);
    }

    /** Para enlazar una cita: el plan sin validar sede (la agenda ya validó la sede de la cita). */
    @Transactional(readOnly = true)
    public Optional<TratamientoResponseDTO> buscarParaCita(UUID id) {
        return tratamientoRepository.findById(id).map(this::toResponseDTO);
    }

    @Transactional
    @Override
    public TratamientoParaCobro validarSaldoParaCobro(UUID pacienteId, UUID tratamientoId, BigDecimal monto) {
        TratamientoEntity tratamiento = tratamientoRepository.findByIdForUpdate(tratamientoId)
                .orElseThrow(() -> new IllegalArgumentException("Tratamiento no encontrado"));
        if (!tratamiento.getPacienteId().equals(pacienteId)) {
            throw new IllegalArgumentException("El tratamiento especificado no corresponde al paciente");
        }
        BigDecimal saldo = tratamiento.getCostoTotal()
                .subtract(tratamiento.getPagadoAntesSistema() != null ? tratamiento.getPagadoAntesSistema() : BigDecimal.ZERO)
                .subtract(pagoRepository.sumMontoPagadoByTratamientoId(tratamientoId));
        if (monto.compareTo(saldo) > 0) {
            throw new PagoExcedeSaldoTratamientoException(saldo);
        }
        BigDecimal saldoRestante = saldo.subtract(monto);
        return new TratamientoParaCobro(tratamiento.getNombre(), tratamiento.getEspecialistaId(), saldoRestante);
    }

    @Transactional
    @Override
    public void verificarYCompletarSiSaldoCero(UUID tratamientoId) {
        if (tratamientoId == null) return;
        tratamientoRepository.findById(tratamientoId).ifPresent(entity -> {
            com.odontomax.tratamientos.domain.model.Tratamiento model =
                    new com.odontomax.tratamientos.domain.model.Tratamiento(
                            entity.getId(),
                            entity.getPacienteId(),
                            entity.getSedeId(),
                            entity.getEspecialistaId(),
                            entity.getNombre(),
                            entity.getEstado(),
                            entity.getTotalSesiones(),
                            entity.getSesionesRealizadas(),
                            entity.getCostoTotal(),
                            entity.getMotivoCierre(),
                            entity.getCerradoEn()
                    );
            BigDecimal pagado = pagoRepository.sumMontoPagadoByTratamientoId(tratamientoId);
            BigDecimal pagadoAntes = entity.getPagadoAntesSistema() != null ? entity.getPagadoAntesSistema() : BigDecimal.ZERO;
            BigDecimal costo = entity.getCostoTotal() != null ? entity.getCostoTotal() : BigDecimal.ZERO;
            BigDecimal saldo = costo
                    .subtract(pagado != null ? pagado : BigDecimal.ZERO)
                    .subtract(pagadoAntes);

            if (model.verificarCierreAutomatico(saldo)) {
                entity.setEstado(model.getEstado());
                entity.setMotivoCierre(model.getMotivoCierre());
                entity.setCerradoEn(model.getCerradoEn());
                tratamientoRepository.save(entity);
            }
        });
    }

    /**
     * Una cita enlazada al plan se atendió: suma una sesión realizada. Un plan planificado pasa a "en proceso". No pasa del
     * total de sesiones ni toca planes completados o cancelados (la cita igual queda atendida); finalizar el plan sigue
     * siendo una decisión explícita.
     */
    @Transactional
    public void registrarSesionDesdeCita(UUID tratamientoId, UUID citaId) {
        TratamientoEntity entity = tratamientoRepository.findById(tratamientoId)
                .orElseThrow(() -> new IllegalArgumentException("Tratamiento no encontrado: " + tratamientoId));

        com.odontomax.tratamientos.domain.model.Tratamiento model =
                new com.odontomax.tratamientos.domain.model.Tratamiento(
                        entity.getId(),
                        entity.getPacienteId(),
                        entity.getSedeId(),
                        entity.getEspecialistaId(),
                        entity.getNombre(),
                        entity.getEstado(),
                        entity.getTotalSesiones(),
                        entity.getSesionesRealizadas(),
                        entity.getCostoTotal(),
                        entity.getMotivoCierre(),
                        entity.getCerradoEn()
                );

        int realizadas = entity.getSesionesRealizadas() != null ? entity.getSesionesRealizadas() : 0;
        Integer total = entity.getTotalSesiones();
        String detalle;
        if (model.esEstadoFinal()) {
            detalle = String.format("Cita %s atendida: el plan '%s' está %s, no se sumó la sesión", citaId, entity.getNombre(), model.getEstado());
        } else if (total != null && total > 0 && realizadas >= total) {
            detalle = String.format("Cita %s atendida: el plan '%s' ya tenía %d/%d sesiones, no se sumó otra (falta finalizarlo)",
                    citaId, entity.getNombre(), realizadas, total);
        } else {
            BigDecimal pagado = pagoRepository.sumMontoPagadoByTratamientoId(tratamientoId);
            BigDecimal pagadoAntes = entity.getPagadoAntesSistema() != null ? entity.getPagadoAntesSistema() : BigDecimal.ZERO;
            BigDecimal costo = entity.getCostoTotal() != null ? entity.getCostoTotal() : BigDecimal.ZERO;
            BigDecimal saldo = costo
                    .subtract(pagado != null ? pagado : BigDecimal.ZERO)
                    .subtract(pagadoAntes);

            model.registrarSesion(saldo);

            entity.setEstado(model.getEstado());
            entity.setSesionesRealizadas(model.getSesionesRealizadas());
            entity.setMotivoCierre(model.getMotivoCierre());
            entity.setCerradoEn(model.getCerradoEn());

            tratamientoRepository.save(entity);
            detalle = String.format("Sesión %d%s del plan '%s' registrada al atender la cita %s",
                    model.getSesionesRealizadas(), total != null && total > 0 ? "/" + total : "", entity.getNombre(), citaId);
        }

        ClinicaUserPrincipal currentUser = SecurityUtils.getCurrentUser();
        auditoriaRepository.save(AuditoriaLogEntity.builder()
                .usuarioId(currentUser != null ? currentUser.id() : null)
                .usuarioNombre(currentUser != null ? currentUser.nombre() : "Sistema")
                .rol(currentUser != null ? currentUser.rol() : "SISTEMA")
                .sedeId(entity.getSedeId())
                .modulo("TRATAMIENTOS")
                .accion("SESION_DESDE_CITA")
                .tabla("tratamientos")
                .registroId(tratamientoId.toString())
                .detalle(detalle)
                .build());
    }

    @Transactional
    public void eliminar(UUID id) {
        TratamientoEntity entity = tratamientoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Tratamiento no encontrado"));

        SecurityUtils.validarAccesoSede(entity.getSedeId());
        consultaEspecialistasApi.exigirAccesoDeOdontologo(entity.getPacienteId());

        BigDecimal pagado = pagoRepository.sumMontoPagadoByTratamientoId(id);
        if (pagado != null && pagado.compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalStateException("No se puede eliminar un tratamiento con pagos activos registrados. Cancele el plan en su lugar.");
        }

        tratamientoRepository.delete(entity);

        ClinicaUserPrincipal currentUser = SecurityUtils.getCurrentUser();
        auditoriaRepository.save(AuditoriaLogEntity.builder()
                .usuarioId(currentUser != null ? currentUser.id() : null)
                .usuarioNombre(currentUser != null ? currentUser.nombre() : "Sistema")
                .rol(currentUser != null ? currentUser.rol() : "SISTEMA")
                .sedeId(entity.getSedeId())
                .modulo("TRATAMIENTOS")
                .accion("ELIMINAR_PLAN")
                .tabla("tratamientos")
                .registroId(id.toString())
                .detalle("Plan de tratamiento eliminado")
                .build());
    }

    private static String nombreCompleto(PacienteResumen p) {
        return (p.nombres() + (p.apellidos() != null && !p.apellidos().isBlank() ? " " + p.apellidos() : "")).trim();
    }

    /** Cuotas vencidas y con saldo de los planes en cuotas en curso de la sede; una fila por plan con la cuota más antigua. */
    @Override
    @Transactional(readOnly = true)
    public List<CuotaVencida> cuotasVencidasDeSede(String sedeId, java.time.LocalDate hoy) {
        List<CuotaVencida> vencidas = new ArrayList<>();
        for (TratamientoEntity t : tratamientoRepository.findBySedeIdAndEstado(sedeId, com.odontomax.tratamientos.domain.model.Tratamiento.ESTADO_EN_PROCESO)) {
            List<PlanCuotaEntity> filas = cuotaRepository.findByTratamientoIdOrderByNumeroAsc(t.getId());
            if (filas.isEmpty()) continue;
            BigDecimal pagado = pagoRepository.sumMontoPagadoByTratamientoId(t.getId());
            BigDecimal pagadoTotal = (pagado != null ? pagado : BigDecimal.ZERO)
                    .add(t.getPagadoAntesSistema() != null ? t.getPagadoAntesSistema() : BigDecimal.ZERO);
            var conEstado = CronogramaCuotas.aplicarPagos(filas.stream()
                    .map(f -> new CronogramaCuotas.Cuota(f.getNumero(), f.getConcepto(), f.getVenceEn(), f.getMonto()))
                    .toList(), pagadoTotal, hoy);
            conEstado.stream().filter(c -> c.estado() == CronogramaCuotas.Estado.VENCIDA).findFirst().ifPresent(c -> {
                var paciente = pacientesApi.findById(t.getPacienteId());
                vencidas.add(new CuotaVencida(t.getId(), t.getPacienteId(),
                        paciente.map(TratamientoService::nombreCompleto).orElse("Paciente"),
                        paciente.map(PacienteResumen::telefono).orElse(null),
                        t.getNombre(), c.cuota().concepto(), c.cuota().venceEn(), c.saldo()));
            });
        }
        return vencidas;
    }

    private TratamientoResponseDTO toResponseDTO(TratamientoEntity t) {
        BigDecimal pagado = pagoRepository.sumMontoPagadoByTratamientoId(t.getId());
        BigDecimal pagadoAntes = t.getPagadoAntesSistema() != null ? t.getPagadoAntesSistema() : BigDecimal.ZERO;
        BigDecimal saldo = t.getCostoTotal()
                .subtract(pagado != null ? pagado : BigDecimal.ZERO)
                .subtract(pagadoAntes);
        if (saldo.compareTo(BigDecimal.ZERO) < 0) {
            saldo = BigDecimal.ZERO;
        }

        List<TratamientoItemResponseDTO> itemsDTO = (t.getItems() != null ? t.getItems() : itemRepository.findByTratamientoId(t.getId()))
                .stream()
                .map(i -> TratamientoItemResponseDTO.builder()
                        .id(i.getId())
                        .tratamientoId(t.getId())
                        .procedimientoId(i.getProcedimientoId())
                        .nombreProcedimiento(i.getNombreProcedimiento())
                        .piezaFdi(i.getPiezaFdi())
                        .precioBase(i.getPrecioBase())
                        .cantidad(i.getCantidad())
                        .descuento(i.getDescuento())
                        .precioFinal(i.getPrecioFinal())
                        .estado(i.getEstado())
                        .observaciones(i.getObservaciones())
                        .gravedad(i.getGravedad())
                        .creadoEn(i.getCreadoEn())
                        .build())
                .toList();

        // Cronograma: lo pagado (cobros + lo cancelado antes del sistema) se reparte en orden entre las cuotas.
        List<CuotaPlanResponseDTO> cuotasDTO = new ArrayList<>();
        int cuotasVencidas = 0;
        List<PlanCuotaEntity> filasCuotas = cuotaRepository.findByTratamientoIdOrderByNumeroAsc(t.getId());
        if (!filasCuotas.isEmpty()) {
            List<CronogramaCuotas.Cuota> cuotas = filasCuotas.stream()
                    .map(f -> new CronogramaCuotas.Cuota(f.getNumero(), f.getConcepto(), f.getVenceEn(), f.getMonto()))
                    .toList();
            BigDecimal pagadoTotal = (pagado != null ? pagado : BigDecimal.ZERO).add(pagadoAntes);
            var conEstado = CronogramaCuotas.aplicarPagos(cuotas, pagadoTotal, java.time.LocalDate.now(java.time.ZoneId.of("America/Lima")));
            cuotasVencidas = (int) CronogramaCuotas.contarVencidas(conEstado);
            for (var ce : conEstado) {
                cuotasDTO.add(CuotaPlanResponseDTO.builder()
                        .numero(ce.cuota().numero())
                        .concepto(ce.cuota().concepto())
                        .venceEn(ce.cuota().venceEn())
                        .monto(ce.cuota().monto())
                        .pagado(ce.pagado())
                        .saldo(ce.saldo())
                        .estado(ce.estado().name())
                        .build());
            }
        }

        List<CambioSesionesResponseDTO> cambiosSesiones = sesionCambioRepository.findByTratamientoIdOrderByCreadoEnAsc(t.getId()).stream()
                .map(c -> CambioSesionesResponseDTO.builder()
                        .sesionesAntes(c.getSesionesAntes())
                        .sesionesDespues(c.getSesionesDespues())
                        .motivo(c.getMotivo())
                        .usuarioNombre(c.getUsuarioNombre())
                        .creadoEn(c.getCreadoEn())
                        .build())
                .toList();

        return TratamientoResponseDTO.builder()
                .id(t.getId())
                .pacienteId(t.getPacienteId())
                .sedeId(t.getSedeId())
                .especialistaId(t.getEspecialistaId())
                .especialistaNombre(t.getEspecialistaId() == null ? null
                        : consultaEspecialistasApi.findNombreById(t.getEspecialistaId()).orElse(null))
                .sedeNombre(sedesApi == null || t.getSedeId() == null ? null : sedesApi.obtenerNombreSede(t.getSedeId()).orElse(null))
                .procedimientoId(t.getProcedimientoId())
                .nombre(t.getNombre())
                .piezaDental(t.getPiezaDental())
                .diagnostico(t.getDiagnostico())
                .modalidadPago(t.getModalidadPago())
                .costoTotal(t.getCostoTotal())
                .descuentoTotal(t.getDescuentoTotal())
                .descuentoAutorizadoPor(t.getDescuentoAutorizadoPor())
                .montoPagado(pagado != null ? pagado : BigDecimal.ZERO)
                .pagadoAntesSistema(pagadoAntes)
                .saldoPendiente(saldo)
                .estado(t.getEstado())
                .aceptadoPorPaciente(t.getAceptadoPorPaciente())
                .fechaAceptacion(t.getFechaAceptacion())
                .fechaInicio(t.getFechaInicio())
                .fechaFinEstimada(t.getFechaFinEstimada())
                .totalSesiones(t.getTotalSesiones())
                .sesionesRealizadas(t.getSesionesRealizadas())
                .proximoControl(t.getProximoControl())
                .requiereSeguimiento(t.getRequiereSeguimiento())
                .observaciones(t.getObservaciones())
                .motivoCierre(t.getMotivoCierre())
                .cerradoEn(t.getCerradoEn())
                .precioCatalogo(t.getPrecioCatalogo())
                .motivoAjustePrecio(t.getMotivoAjustePrecio())
                .cuotas(cuotasDTO)
                .cuotasVencidas(cuotasVencidas)
                .items(itemsDTO)
                .creadoEn(t.getCreadoEn())
                .actualizadoEn(t.getActualizadoEn())
                .cambiosSesiones(cambiosSesiones)
                .build();
    }
}
