package com.odontomax.laboratorio.service;

import com.odontomax.agenda.infrastructure.persistence.entity.EspecialistaEntity;
import com.odontomax.agenda.api.ConsultaEspecialistasModuloApi;
import com.odontomax.agenda.infrastructure.persistence.repository.SpringDataEspecialistaRepository;
import com.odontomax.caja.api.CajaEgresoModuloApi;
import com.odontomax.laboratorio.domain.model.CuentaLaboratorio;
import com.odontomax.laboratorio.domain.model.EstadoPagoLaboratorio;
import com.odontomax.laboratorio.dto.LaboratorioCuentaResponseDTO;
import com.odontomax.laboratorio.dto.LaboratorioExternoRequestDTO;
import com.odontomax.laboratorio.dto.OrdenLaboratorioCuentaDTO;
import com.odontomax.laboratorio.dto.OrdenLaboratorioCambioEstadoRequestDTO;
import com.odontomax.laboratorio.dto.OrdenLaboratorioCreateRequestDTO;
import com.odontomax.laboratorio.dto.OrdenLaboratorioUpdateRequestDTO;
import com.odontomax.laboratorio.dto.LaboratorioExternoResponseDTO;
import com.odontomax.laboratorio.dto.OrdenLaboratorioHistorialResponseDTO;
import com.odontomax.laboratorio.dto.OrdenLaboratorioResponseDTO;
import com.odontomax.laboratorio.dto.PagoLaboratorioResponseDTO;
import com.odontomax.laboratorio.dto.AnularPagoLaboratorioRequestDTO;
import com.odontomax.laboratorio.dto.RegistrarPagoLaboratorioRequestDTO;
import java.time.Instant;
import com.odontomax.identidad.api.ClinicaUserPrincipal;
import com.odontomax.identidad.api.SecurityUtils;
import com.odontomax.infrastructure.persistence.entity.AuditoriaLogEntity;
import com.odontomax.infrastructure.persistence.entity.LaboratorioExternoEntity;
import com.odontomax.infrastructure.persistence.entity.OrdenLaboratorioEntity;
import com.odontomax.infrastructure.persistence.entity.OrdenLaboratorioHistorialEntity;
import com.odontomax.infrastructure.persistence.entity.PagoLaboratorioEntity;
import com.odontomax.pacientes.api.ConsultaPacientesModuloApi;
import com.odontomax.pacientes.api.PacienteResumen;
import com.odontomax.infrastructure.persistence.repository.SpringDataAuditoriaRepository;
import com.odontomax.infrastructure.persistence.repository.SpringDataLaboratorioExternoRepository;
import com.odontomax.infrastructure.persistence.repository.SpringDataPagoLaboratorioRepository;
import com.odontomax.infrastructure.persistence.repository.SpringDataOrdenLaboratorioHistorialRepository;
import com.odontomax.infrastructure.persistence.repository.SpringDataOrdenLaboratorioRepository;
import com.odontomax.infrastructure.persistence.repository.SpringDataOrdenLaboratorioPruebaRepository;
import com.odontomax.infrastructure.persistence.entity.OrdenLaboratorioPruebaEntity;
import com.odontomax.laboratorio.dto.OrdenLaboratorioPruebaResponseDTO;
import com.odontomax.laboratorio.dto.RegistrarPruebaLaboratorioRequestDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LaboratorioService {

    private final SpringDataLaboratorioExternoRepository laboratorioRepository;
    private final SpringDataOrdenLaboratorioRepository ordenRepository;
    private final SpringDataOrdenLaboratorioHistorialRepository historialRepository;
    private final ConsultaPacientesModuloApi pacientesApi;
    private final SpringDataEspecialistaRepository especialistaRepository;
    private final SpringDataAuditoriaRepository auditoriaRepository;
    private final ConsultaEspecialistasModuloApi consultaEspecialistasApi;
    private final SpringDataPagoLaboratorioRepository pagoLaboratorioRepository;
    private final SpringDataOrdenLaboratorioPruebaRepository pruebaRepository;
    private final CajaEgresoModuloApi cajaEgresoModuloApi;

    /** Flujo corto: enviado al laboratorio → en prueba con el paciente (1, 2, 3…) → finalizado. */
    private static final Set<String> ESTADOS_VALIDOS = Set.of("ENVIADO", "EN_PRUEBA", "FINALIZADO", "CANCELADO");

    // =========================================================================
    // Catálogo de Laboratorios Externos
    // =========================================================================

    @Transactional(readOnly = true)
    public List<LaboratorioExternoResponseDTO> listarLaboratoriosExternos(Boolean soloActivos) {
        List<LaboratorioExternoEntity> list = Boolean.FALSE.equals(soloActivos)
                ? laboratorioRepository.findAll()
                : laboratorioRepository.findByActivoTrueOrderByNombreAsc();

        return list.stream().map(this::toLaboratorioDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public LaboratorioExternoResponseDTO obtenerLaboratorioPorId(UUID id) {
        LaboratorioExternoEntity entity = laboratorioRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Laboratorio dental no encontrado"));
        return toLaboratorioDTO(entity);
    }

    @Transactional
    public LaboratorioExternoResponseDTO crearLaboratorioExterno(LaboratorioExternoRequestDTO req, ClinicaUserPrincipal user) {
        LaboratorioExternoEntity entity = LaboratorioExternoEntity.builder()
                .nombre(req.getNombre().trim())
                .contacto(req.getContacto())
                .telefono(req.getTelefono())
                .email(req.getEmail())
                .direccion(req.getDireccion())
                .tiempoEntregaPromedioDias(req.getTiempoEntregaPromedioDias() != null ? req.getTiempoEntregaPromedioDias() : 5)
                .activo(req.getActivo() == null || req.getActivo())
                .build();

        LaboratorioExternoEntity guardado = laboratorioRepository.save(entity);
        registrarAuditoria(user, null, "CREAR_LABORATORIO_EXTERNO",
                "Creado laboratorio dental externo: " + guardado.getNombre());

        return toLaboratorioDTO(guardado);
    }

    @Transactional
    public LaboratorioExternoResponseDTO actualizarLaboratorioExterno(UUID id, LaboratorioExternoRequestDTO req, ClinicaUserPrincipal user) {
        LaboratorioExternoEntity entity = laboratorioRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Laboratorio dental no encontrado"));

        entity.setNombre(req.getNombre().trim());
        entity.setContacto(req.getContacto());
        entity.setTelefono(req.getTelefono());
        entity.setEmail(req.getEmail());
        entity.setDireccion(req.getDireccion());
        if (req.getTiempoEntregaPromedioDias() != null) {
            entity.setTiempoEntregaPromedioDias(req.getTiempoEntregaPromedioDias());
        }
        if (req.getActivo() != null) {
            entity.setActivo(req.getActivo());
        }

        LaboratorioExternoEntity guardado = laboratorioRepository.save(entity);
        registrarAuditoria(user, null, "ACTUALIZAR_LABORATORIO_EXTERNO",
                "Actualizado laboratorio dental externo: " + guardado.getNombre());

        return toLaboratorioDTO(guardado);
    }

    // =========================================================================
    // Órdenes de Trabajo de Prótesis y Laboratorio
    // =========================================================================

    @Transactional(readOnly = true)
    public List<OrdenLaboratorioResponseDTO> listarOrdenes(
            String sedeId,
            String estado,
            UUID pacienteId,
            UUID especialistaId,
            ClinicaUserPrincipal user
    ) {
        String sedeEfectiva = sedeId;
        if (user != null && !"ADMIN_GENERAL".equalsIgnoreCase(user.rol()) && user.sedeId() != null) {
            sedeEfectiva = user.sedeId();
        }

        List<OrdenLaboratorioEntity> ordenes = ordenRepository.buscarOrdenes(
                (sedeEfectiva != null && !sedeEfectiva.isBlank()) ? sedeEfectiva : null,
                (estado != null && !estado.isBlank()) ? estado.toUpperCase() : null,
                pacienteId,
                especialistaId
        );
        if (sedeEfectiva == null || sedeEfectiva.isBlank()) {
            // "Todas las sedes" del administrador general = todas las del negocio activo.
            ordenes = ordenes.stream().filter(o -> SecurityUtils.sedeEnNegocioActivo(o.getSedeId())).toList();
        }

        boolean esOdontologo = user != null && user.soloOdontologo();
        if (esOdontologo && user != null) {
            Set<UUID> autorizados = consultaEspecialistasApi.pacientesDelOdontologo(user.id());
            ordenes = ordenes.stream()
                    .filter(o -> autorizados.contains(o.getPacienteId()))
                    .toList();
        }

        Map<UUID, PacienteResumen> pacientes = pacientesApi.findAllById(
                ordenes.stream().map(OrdenLaboratorioEntity::getPacienteId).collect(Collectors.toSet())
        );

        Map<UUID, EspecialistaEntity> especialistas = especialistaRepository.findAllById(
                ordenes.stream().map(OrdenLaboratorioEntity::getEspecialistaId).collect(Collectors.toSet())
        ).stream().collect(Collectors.toMap(EspecialistaEntity::getId, e -> e, (a, b) -> a));

        Map<UUID, LaboratorioExternoEntity> labs = laboratorioRepository.findAllById(
                ordenes.stream().map(OrdenLaboratorioEntity::getLaboratorioId).collect(Collectors.toSet())
        ).stream().collect(Collectors.toMap(LaboratorioExternoEntity::getId, l -> l, (a, b) -> a));

        boolean incluirFinanzas = !esOdontologo;
        Map<UUID, java.math.BigDecimal> pagosPorOrden = incluirFinanzas
                ? pagosPorOrden(ordenes.stream().map(OrdenLaboratorioEntity::getId).toList())
                : Map.of();

        return ordenes.stream()
                .map(o -> toOrdenDTO(o, pacientes.get(o.getPacienteId()), especialistas.get(o.getEspecialistaId()),
                        labs.get(o.getLaboratorioId()), false, incluirFinanzas,
                        pagosPorOrden.getOrDefault(o.getId(), java.math.BigDecimal.ZERO)))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public OrdenLaboratorioResponseDTO obtenerOrdenPorId(UUID id) {
        return obtenerOrdenPorId(id, SecurityUtils.getCurrentUser());
    }

    @Transactional(readOnly = true)
    public OrdenLaboratorioResponseDTO obtenerOrdenPorId(UUID id, ClinicaUserPrincipal user) {
        OrdenLaboratorioEntity orden = ordenRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Orden de laboratorio no encontrada"));

        consultaEspecialistasApi.exigirAccesoDeOdontologo(orden.getPacienteId());

        PacienteResumen paciente = pacientesApi.findById(orden.getPacienteId()).orElse(null);
        EspecialistaEntity especialista = especialistaRepository.findById(orden.getEspecialistaId()).orElse(null);
        LaboratorioExternoEntity lab = laboratorioRepository.findById(orden.getLaboratorioId()).orElse(null);

        boolean incluirFinanzas = user == null || !user.soloOdontologo();
        java.math.BigDecimal pagado = incluirFinanzas
                ? montoPagado(orden.getId())
                : java.math.BigDecimal.ZERO;
        return toOrdenDTO(orden, paciente, especialista, lab, true, incluirFinanzas, pagado);
    }

    @Transactional
    public OrdenLaboratorioResponseDTO crearOrden(OrdenLaboratorioCreateRequestDTO req, ClinicaUserPrincipal user) {
        consultaEspecialistasApi.exigirAccesoDeOdontologo(req.getPacienteId());

        PacienteResumen paciente = pacientesApi.findById(req.getPacienteId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Paciente no encontrado"));

        EspecialistaEntity especialista = especialistaRepository.findById(req.getEspecialistaId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Especialista no encontrado"));

        LaboratorioExternoEntity lab = laboratorioRepository.findById(req.getLaboratorioId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Laboratorio dental no encontrado"));

        String codigo = generarCodigoOrden();
        String usuarioNombre = user != null ? user.nombre() : "Sistema";
        String usuarioRol = user != null ? user.rol() : "RECEPCION";
        boolean incluirFinanzas = user == null || !user.soloOdontologo();

        OrdenLaboratorioEntity orden = OrdenLaboratorioEntity.builder()
                .codigo(codigo)
                .pacienteId(req.getPacienteId())
                .especialistaId(req.getEspecialistaId())
                .sedeId(req.getSedeId())
                .laboratorioId(req.getLaboratorioId())
                .tipoTrabajo(req.getTipoTrabajo().trim())
                .colorTono(req.getColorEfectivo() != null ? req.getColorEfectivo().trim() : null)
                .material(req.getMaterial() != null ? req.getMaterial().trim() : null)
                .provisional(req.getProvisional() != null ? req.getProvisional() : false)
                .piezasDentales(req.getPiezasDentales() != null ? req.getPiezasDentales().trim() : null)
                .estado("ENVIADO")
                .fechaSolicitud(LocalDate.now())
                .fechaEnvioLaboratorio(OffsetDateTime.now())
                .fechaEstimadaEntrega(req.getFechaEstimadaEntrega() != null ? req.getFechaEstimadaEntrega() : LocalDate.now().plusDays(lab.getTiempoEntregaPromedioDias() != null ? lab.getTiempoEntregaPromedioDias() : 5))
                .costoLaboratorio(incluirFinanzas ? req.getCostoLaboratorio() : null)
                .precioPaciente(incluirFinanzas ? req.getPrecioPaciente() : null)
                .guiaRemision(req.getGuiaRemision())
                .indicacionesTecnicas(req.getIndicacionesTecnicas())
                .observaciones(req.getObservaciones())
                .registradoPor(usuarioNombre)
                .build();

        OrdenLaboratorioEntity guardada = ordenRepository.save(orden);

        // Guardar entrada de creación en historial
        historialRepository.save(OrdenLaboratorioHistorialEntity.builder()
                .ordenId(guardada.getId())
                .estadoAnterior(null)
                .estadoNuevo("ENVIADO")
                .usuarioNombre(usuarioNombre)
                .usuarioRol(usuarioRol)
                .motivo("Orden creada y enviada al laboratorio")
                .build());

        registrarAuditoria(user, req.getSedeId(), "CREAR_ORDEN_LABORATORIO",
                String.format("Creada orden %s (%s) para paciente %s %s en lab %s",
                        guardada.getCodigo(), guardada.getTipoTrabajo(), paciente.nombres(), paciente.apellidos(), lab.getNombre()));

        java.math.BigDecimal pagado = incluirFinanzas ? montoPagado(guardada.getId()) : java.math.BigDecimal.ZERO;
        return toOrdenDTO(guardada, paciente, especialista, lab, true, incluirFinanzas, pagado);
    }

    @Transactional
    public OrdenLaboratorioResponseDTO actualizarOrden(UUID id, OrdenLaboratorioUpdateRequestDTO req, ClinicaUserPrincipal user) {
        OrdenLaboratorioEntity orden = ordenRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Orden de laboratorio no encontrada"));

        consultaEspecialistasApi.exigirAccesoDeOdontologo(orden.getPacienteId());

        if ("CANCELADO".equalsIgnoreCase(orden.getEstado()) || "FINALIZADO".equalsIgnoreCase(orden.getEstado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No se puede modificar una orden que ya está finalizada o cancelada");
        }

        LaboratorioExternoEntity lab = laboratorioRepository.findById(req.getLaboratorioId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Laboratorio dental no encontrado"));

        orden.setLaboratorioId(req.getLaboratorioId());
        orden.setTipoTrabajo(req.getTipoTrabajo().trim());
        String colorEfectivo = req.getColorEfectivo();
        if (colorEfectivo != null) {
            orden.setColorTono(colorEfectivo.trim());
        }
        if (req.getMaterial() != null) {
            orden.setMaterial(req.getMaterial().trim());
        }
        if (req.getProvisional() != null) {
            orden.setProvisional(req.getProvisional());
        }
        orden.setPiezasDentales(req.getPiezasDentales() != null ? req.getPiezasDentales().trim() : null);
        orden.setFechaEstimadaEntrega(req.getFechaEstimadaEntrega());
        boolean incluirFinanzas = user == null || !user.soloOdontologo();
        if (incluirFinanzas) {
            orden.setCostoLaboratorio(req.getCostoLaboratorio());
            orden.setPrecioPaciente(req.getPrecioPaciente());
        }
        orden.setGuiaRemision(req.getGuiaRemision());
        orden.setIndicacionesTecnicas(req.getIndicacionesTecnicas());
        orden.setObservaciones(req.getObservaciones());

        OrdenLaboratorioEntity guardada = ordenRepository.save(orden);

        PacienteResumen paciente = pacientesApi.findById(guardada.getPacienteId()).orElse(null);
        EspecialistaEntity especialista = especialistaRepository.findById(guardada.getEspecialistaId()).orElse(null);

        registrarAuditoria(user, guardada.getSedeId(), "ACTUALIZAR_ORDEN_LABORATORIO",
                String.format("Actualizada orden %s (%s)", guardada.getCodigo(), guardada.getTipoTrabajo()));

        java.math.BigDecimal pagado = incluirFinanzas ? montoPagado(guardada.getId()) : java.math.BigDecimal.ZERO;
        return toOrdenDTO(guardada, paciente, especialista, lab, true, incluirFinanzas, pagado);
    }

    @Transactional
    public OrdenLaboratorioResponseDTO cambiarEstado(UUID id, OrdenLaboratorioCambioEstadoRequestDTO req, ClinicaUserPrincipal user) {
        OrdenLaboratorioEntity orden = ordenRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Orden de laboratorio no encontrada"));

        consultaEspecialistasApi.exigirAccesoDeOdontologo(orden.getPacienteId());

        String nuevo = req.getNuevoEstado() != null ? req.getNuevoEstado().trim().toUpperCase() : "";
        if (!ESTADOS_VALIDOS.contains(nuevo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Estado inválido: " + nuevo);
        }

        String anterior = orden.getEstado();
        if (anterior.equalsIgnoreCase(nuevo)) {
            // Ya se encuentra en este estado
            return obtenerOrdenPorId(id, user);
        }

        if ("CANCELADO".equalsIgnoreCase(anterior)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No se puede cambiar el estado de una orden cancelada.");
        }
        if ("FINALIZADO".equalsIgnoreCase(anterior)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El trabajo ya fue finalizado.");
        }

        // Reglas de negocio de tiempos
        OffsetDateTime ahora = OffsetDateTime.now();
        if ("ENVIADO".equalsIgnoreCase(nuevo)) {
            orden.setFechaEnvioLaboratorio(ahora);
        } else if ("EN_PRUEBA".equalsIgnoreCase(nuevo)) {
            orden.setFechaRecepcionClinica(ahora);
        } else if ("FINALIZADO".equalsIgnoreCase(nuevo)) {
            orden.setFechaInstalacion(ahora);
        }

        if (req.getGuiaRemision() != null && !req.getGuiaRemision().isBlank()) {
            orden.setGuiaRemision(req.getGuiaRemision().trim());
        }

        orden.setEstado(nuevo);
        OrdenLaboratorioEntity guardada = ordenRepository.save(orden);

        String usuarioNombre = user != null ? user.nombre() : "Personal Clínico";
        String usuarioRol = user != null ? user.rol() : "CLINICO";

        historialRepository.save(OrdenLaboratorioHistorialEntity.builder()
                .ordenId(guardada.getId())
                .estadoAnterior(anterior)
                .estadoNuevo(nuevo)
                .usuarioNombre(usuarioNombre)
                .usuarioRol(usuarioRol)
                .motivo(req.getMotivo() != null && !req.getMotivo().isBlank() ? req.getMotivo().trim() : "Transición a " + nuevo)
                .build());

        registrarAuditoria(user, guardada.getSedeId(), "CAMBIAR_ESTADO_ORDEN_LAB",
                String.format("Orden %s cambió de %s a %s. Motivo: %s",
                        guardada.getCodigo(), anterior, nuevo, req.getMotivo()));

        PacienteResumen paciente = pacientesApi.findById(guardada.getPacienteId()).orElse(null);
        EspecialistaEntity especialista = especialistaRepository.findById(guardada.getEspecialistaId()).orElse(null);
        LaboratorioExternoEntity lab = laboratorioRepository.findById(guardada.getLaboratorioId()).orElse(null);

        boolean incluirFinanzas = user == null || !user.soloOdontologo();
        java.math.BigDecimal pagado = incluirFinanzas ? montoPagado(guardada.getId()) : java.math.BigDecimal.ZERO;
        return toOrdenDTO(guardada, paciente, especialista, lab, true, incluirFinanzas, pagado);
    }

    /**
     * Registra una prueba del trabajo con el paciente. Correcta: el trabajo se finaliza. Con ajuste: vuelve al laboratorio
     * y la siguiente será la prueba siguiente.
     */
    @Transactional
    public OrdenLaboratorioResponseDTO registrarPrueba(UUID id, RegistrarPruebaLaboratorioRequestDTO req, ClinicaUserPrincipal user) {
        OrdenLaboratorioEntity orden = ordenRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Orden de laboratorio no encontrada"));
        consultaEspecialistasApi.exigirAccesoDeOdontologo(orden.getPacienteId());
        SecurityUtils.validarAccesoSede(orden.getSedeId());

        String anterior = orden.getEstado();
        if (!"ENVIADO".equalsIgnoreCase(anterior) && !"EN_PRUEBA".equalsIgnoreCase(anterior)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Solo se registran pruebas de un trabajo en curso (no finalizado ni cancelado).");
        }

        String resultado = req.getResultado().trim().toUpperCase(Locale.ROOT);
        int numero = (int) pruebaRepository.countByOrdenId(id) + 1;
        String usuarioNombre = user != null ? user.nombre() : "Personal Clínico";
        pruebaRepository.save(OrdenLaboratorioPruebaEntity.builder()
                .ordenId(id)
                .numero(numero)
                .fecha(req.getFecha() != null ? req.getFecha() : LocalDate.now())
                .resultado(resultado)
                .notas(req.getNotas() != null && !req.getNotas().isBlank() ? req.getNotas().trim() : null)
                .registradoPor(usuarioNombre)
                .build());

        OffsetDateTime ahora = OffsetDateTime.now();
        String nuevo;
        if ("CORRECTO".equals(resultado)) {
            nuevo = "FINALIZADO";
            if (orden.getFechaRecepcionClinica() == null) {
                orden.setFechaRecepcionClinica(ahora);
            }
            orden.setFechaInstalacion(ahora);
        } else {
            nuevo = "ENVIADO";
            orden.setFechaEnvioLaboratorio(ahora);
            if (req.getNuevaFechaEntrega() != null) {
                orden.setFechaEstimadaEntrega(req.getNuevaFechaEntrega());
            }
        }
        orden.setEstado(nuevo);
        OrdenLaboratorioEntity guardada = ordenRepository.save(orden);

        String motivo = "Prueba " + numero + ("CORRECTO".equals(resultado) ? ": correcta, trabajo finalizado" : ": necesita ajuste, vuelve al laboratorio")
                + (req.getNotas() != null && !req.getNotas().isBlank() ? ". " + req.getNotas().trim() : "");
        historialRepository.save(OrdenLaboratorioHistorialEntity.builder()
                .ordenId(guardada.getId())
                .estadoAnterior(anterior)
                .estadoNuevo(nuevo)
                .usuarioNombre(usuarioNombre)
                .usuarioRol(user != null ? user.rol() : "CLINICO")
                .motivo(motivo)
                .build());
        registrarAuditoria(user, guardada.getSedeId(), "PRUEBA_ORDEN_LAB",
                String.format("Orden %s: %s", guardada.getCodigo(), motivo));

        PacienteResumen paciente = pacientesApi.findById(guardada.getPacienteId()).orElse(null);
        EspecialistaEntity especialista = especialistaRepository.findById(guardada.getEspecialistaId()).orElse(null);
        LaboratorioExternoEntity lab = laboratorioRepository.findById(guardada.getLaboratorioId()).orElse(null);
        boolean incluirFinanzas = user == null || !user.soloOdontologo();
        java.math.BigDecimal pagado = incluirFinanzas ? montoPagado(guardada.getId()) : java.math.BigDecimal.ZERO;
        return toOrdenDTO(guardada, paciente, especialista, lab, true, incluirFinanzas, pagado);
    }

    @Transactional
    public PagoLaboratorioResponseDTO registrarPagoLaboratorio(
            UUID ordenId,
            RegistrarPagoLaboratorioRequestDTO req,
            ClinicaUserPrincipal user
    ) {
        validarAccesoFinanciero(user);
        OrdenLaboratorioEntity orden = ordenRepository.findByIdForUpdate(ordenId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Orden de laboratorio no encontrada"));
        SecurityUtils.validarAccesoSede(orden.getSedeId());
        java.math.BigDecimal pagado = montoPagado(ordenId);
        CuentaLaboratorio.validarPago(orden.getEstado(), orden.getCostoLaboratorio(), pagado, req.getMonto());

        String metodo = req.getMetodoPago() == null ? "" : req.getMetodoPago().trim().toUpperCase(Locale.ROOT);
        if (!Set.of("EFECTIVO", "YAPE", "PLIN").contains(metodo)) {
            throw new IllegalArgumentException("El método de pago debe ser EFECTIVO, YAPE o PLIN.");
        }
        LaboratorioExternoEntity laboratorio = laboratorioRepository.findById(orden.getLaboratorioId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Laboratorio dental no encontrado"));

        PagoLaboratorioEntity pago = pagoLaboratorioRepository.save(PagoLaboratorioEntity.builder()
                .ordenId(ordenId)
                .laboratorioId(orden.getLaboratorioId())
                .sedeId(orden.getSedeId())
                .monto(req.getMonto())
                .metodoPago(metodo)
                .numeroOperacion(limpiar(req.getNumeroOperacion()))
                .registradoPor(user.id())
                .notas(limpiar(req.getNotas()))
                .build());

        String concepto = String.format("Laboratorio %s · %s", laboratorio.getNombre(), orden.getCodigo());
        UUID sesionId = cajaEgresoModuloApi.registrarEgresoDeSistema(
                orden.getSedeId(), req.getMonto(), metodo, concepto, "PAGO_LABORATORIO", pago.getId());
        pago.setCajaSesionId(sesionId);
        PagoLaboratorioEntity guardado = pagoLaboratorioRepository.save(pago);

        auditoriaRepository.save(AuditoriaLogEntity.builder()
                .usuarioId(user.id())
                .usuarioNombre(user.nombre())
                .rol(user.rol())
                .sedeId(orden.getSedeId())
                .modulo("LABORATORIO")
                .accion("REGISTRAR_PAGO_LABORATORIO")
                .tabla("pagos_laboratorio")
                .registroId(guardado.getId().toString())
                .detalle(String.format("Pago de S/ %s al laboratorio %s para la orden %s (%s)",
                        guardado.getMonto(), laboratorio.getNombre(), orden.getCodigo(), metodo))
                .build());

        return toPagoLaboratorioDTO(guardado);
    }

    @Transactional
    public PagoLaboratorioResponseDTO anularPagoLaboratorio(
            UUID pagoId,
            AnularPagoLaboratorioRequestDTO req,
            ClinicaUserPrincipal user
    ) {
        if (user == null || (!"ADMIN_GENERAL".equalsIgnoreCase(user.rol()) && !"ADMIN_SEDE".equalsIgnoreCase(user.rol()))) {
            throw new org.springframework.security.access.AccessDeniedException("Solo los administradores pueden anular pagos de laboratorio");
        }
        PagoLaboratorioEntity pago = pagoLaboratorioRepository.findById(pagoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pago de laboratorio no encontrado"));

        SecurityUtils.validarAccesoSede(pago.getSedeId());

        String motivo = req != null && req.getMotivoAnulacion() != null ? req.getMotivoAnulacion().trim() : "";
        CuentaLaboratorio.validarAnulacion(pago.getAnulado(), motivo);

        LaboratorioExternoEntity laboratorio = laboratorioRepository.findById(pago.getLaboratorioId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Laboratorio dental no encontrado"));
        OrdenLaboratorioEntity orden = ordenRepository.findById(pago.getOrdenId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Orden de laboratorio no encontrada"));

        pago.setAnulado(true);
        pago.setAnuladoEn(Instant.now());
        pago.setAnuladoPor(user.id());
        pago.setMotivoAnulacion(motivo);

        PagoLaboratorioEntity guardado = pagoLaboratorioRepository.save(pago);

        String concepto = String.format("Anulación de pago a laboratorio %s · %s: %s",
                laboratorio.getNombre(), orden.getCodigo(), motivo);
        cajaEgresoModuloApi.revertirEgresoDeSistema(
                pago.getSedeId(), pago.getMonto(), pago.getMetodoPago(), concepto, "PAGO_LABORATORIO", pago.getId());

        auditoriaRepository.save(AuditoriaLogEntity.builder()
                .usuarioId(user.id())
                .usuarioNombre(user.nombre())
                .rol(user.rol())
                .sedeId(pago.getSedeId())
                .modulo("LABORATORIO")
                .accion("ANULAR_PAGO_LABORATORIO")
                .tabla("pagos_laboratorio")
                .registroId(guardado.getId().toString())
                .detalle(String.format("Pago de S/ %s al laboratorio %s anulado. Motivo: %s",
                        guardado.getMonto(), laboratorio.getNombre(), motivo))
                .build());

        return toPagoLaboratorioDTO(guardado);
    }

    @Transactional(readOnly = true)
    public List<PagoLaboratorioResponseDTO> listarPagosLaboratorio(UUID ordenId, ClinicaUserPrincipal user) {
        validarAccesoFinanciero(user);
        OrdenLaboratorioEntity orden = ordenRepository.findById(ordenId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Orden de laboratorio no encontrada"));
        SecurityUtils.validarAccesoSede(orden.getSedeId());
        return pagoLaboratorioRepository.findByOrdenIdOrderByPagadoEnAsc(ordenId).stream()
                .map(this::toPagoLaboratorioDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LaboratorioCuentaResponseDTO> listarCuentasLaboratorios(String sedeId, UUID laboratorioId,
                                                                        ClinicaUserPrincipal user) {
        validarAccesoFinanciero(user);
        String sedeEfectiva = sedeId;
        if (!"ADMIN_GENERAL".equalsIgnoreCase(user.rol()) && user.sedeId() != null) {
            sedeEfectiva = user.sedeId();
        }
        if (sedeEfectiva != null && !sedeEfectiva.isBlank()) {
            SecurityUtils.validarAccesoSede(sedeEfectiva);
        }

        List<OrdenLaboratorioEntity> ordenes = ordenRepository.buscarOrdenesParaCuenta(
                sedeEfectiva != null && !sedeEfectiva.isBlank() ? sedeEfectiva : null,
                laboratorioId);
        if (sedeEfectiva == null || sedeEfectiva.isBlank()) {
            ordenes = ordenes.stream().filter(o -> SecurityUtils.sedeEnNegocioActivo(o.getSedeId())).toList();
        }
        Map<UUID, java.math.BigDecimal> pagos = pagosPorOrden(
                ordenes.stream().map(OrdenLaboratorioEntity::getId).toList());
        Map<UUID, LaboratorioExternoEntity> laboratorios = laboratorioRepository.findAllById(
                        ordenes.stream().map(OrdenLaboratorioEntity::getLaboratorioId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(LaboratorioExternoEntity::getId, l -> l, (a, b) -> a));

        Map<UUID, List<OrdenLaboratorioEntity>> porLaboratorio = ordenes.stream()
                .collect(Collectors.groupingBy(OrdenLaboratorioEntity::getLaboratorioId, LinkedHashMap::new, Collectors.toList()));
        return porLaboratorio.entrySet().stream().map(entry -> {
            List<OrdenLaboratorioCuentaDTO> cuentasOrden = entry.getValue().stream().map(orden -> {
                java.math.BigDecimal montoPagado = pagos.getOrDefault(orden.getId(), java.math.BigDecimal.ZERO);
                EstadoPagoLaboratorio estadoPago = EstadoPagoLaboratorio.derivar(orden.getCostoLaboratorio(), montoPagado);
                return OrdenLaboratorioCuentaDTO.builder()
                        .ordenId(orden.getId())
                        .codigo(orden.getCodigo())
                        .sedeId(orden.getSedeId())
                        .costoLaboratorio(orden.getCostoLaboratorio())
                        .montoPagado(montoPagado)
                        .saldoLaboratorio(CuentaLaboratorio.saldo(orden.getCostoLaboratorio(), montoPagado))
                        .estadoPago(estadoPago != null ? estadoPago.name() : null)
                        .build();
            }).toList();
            java.math.BigDecimal facturado = entry.getValue().stream()
                    .map(OrdenLaboratorioEntity::getCostoLaboratorio)
                    .filter(Objects::nonNull)
                    .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
            java.math.BigDecimal totalPagado = entry.getValue().stream()
                    .map(o -> pagos.getOrDefault(o.getId(), java.math.BigDecimal.ZERO))
                    .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
            LaboratorioExternoEntity laboratorio = laboratorios.get(entry.getKey());
            return LaboratorioCuentaResponseDTO.builder()
                    .laboratorioId(entry.getKey())
                    .laboratorioNombre(laboratorio != null ? laboratorio.getNombre() : "Laboratorio")
                    .totalFacturado(facturado)
                    .totalPagado(totalPagado)
                    .saldoPorPagar(facturado.subtract(totalPagado).max(java.math.BigDecimal.ZERO))
                    .ordenes(cuentasOrden)
                    .build();
        }).toList();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private synchronized String generarCodigoOrden() {
        int anio = LocalDate.now().getYear();
        long count = ordenRepository.count() + 1;
        String codigo = String.format("LAB-%d-%04d", anio, count);
        while (ordenRepository.findByCodigo(codigo).isPresent()) {
            count++;
            codigo = String.format("LAB-%d-%04d", anio, count);
        }
        return codigo;
    }

    private LaboratorioExternoResponseDTO toLaboratorioDTO(LaboratorioExternoEntity entity) {
        return LaboratorioExternoResponseDTO.builder()
                .id(entity.getId())
                .nombre(entity.getNombre())
                .contacto(entity.getContacto())
                .telefono(entity.getTelefono())
                .email(entity.getEmail())
                .direccion(entity.getDireccion())
                .tiempoEntregaPromedioDias(entity.getTiempoEntregaPromedioDias())
                .activo(entity.isActivo())
                .creadoEn(entity.getCreadoEn())
                .actualizadoEn(entity.getActualizadoEn())
                .build();
    }

    private OrdenLaboratorioResponseDTO toOrdenDTO(
            OrdenLaboratorioEntity orden,
            PacienteResumen paciente,
            EspecialistaEntity especialista,
            LaboratorioExternoEntity lab,
            boolean incluirHistorial,
            boolean incluirFinanzas,
            java.math.BigDecimal montoPagado
    ) {
        String pacienteNombre = paciente != null ? (paciente.nombres() + " " + paciente.apellidos()).trim() : "Paciente";
        String pacienteDni = paciente != null ? paciente.dni() : null;
        String espNombre = especialista != null ? especialista.getNombre() : "Odontólogo Tratante";
        String labNombre = lab != null ? lab.getNombre() : "Laboratorio Externo";

        List<OrdenLaboratorioHistorialResponseDTO> historialDTO = Collections.emptyList();
        if (incluirHistorial) {
            historialDTO = historialRepository.findByOrdenIdOrderByCreadoEnAsc(orden.getId()).stream()
                    .map(h -> OrdenLaboratorioHistorialResponseDTO.builder()
                            .id(h.getId())
                            .ordenId(h.getOrdenId())
                            .estadoAnterior(h.getEstadoAnterior())
                            .estadoNuevo(h.getEstadoNuevo())
                            .usuarioNombre(h.getUsuarioNombre())
                            .usuarioRol(h.getUsuarioRol())
                            .motivo(h.getMotivo())
                            .creadoEn(h.getCreadoEn())
                            .build())
                    .collect(Collectors.toList());
        }

        List<OrdenLaboratorioPruebaResponseDTO> pruebasDTO = incluirHistorial
                ? pruebaRepository.findByOrdenIdOrderByNumeroAsc(orden.getId()).stream()
                        .map(p -> OrdenLaboratorioPruebaResponseDTO.builder()
                                .id(p.getId())
                                .numero(p.getNumero())
                                .fecha(p.getFecha())
                                .resultado(p.getResultado())
                                .notas(p.getNotas())
                                .registradoPor(p.getRegistradoPor())
                                .build())
                        .collect(Collectors.toList())
                : Collections.emptyList();

        OrdenLaboratorioResponseDTO.OrdenLaboratorioResponseDTOBuilder dto = OrdenLaboratorioResponseDTO.builder()
                .id(orden.getId())
                .codigo(orden.getCodigo())
                .pacienteId(orden.getPacienteId())
                .pacienteNombre(pacienteNombre)
                .pacienteDni(pacienteDni)
                .especialistaId(orden.getEspecialistaId())
                .especialistaNombre(espNombre)
                .sedeId(orden.getSedeId())
                .laboratorioId(orden.getLaboratorioId())
                .laboratorioNombre(labNombre)
                .tipoTrabajo(orden.getTipoTrabajo())
                .colorTono(orden.getColorTono())
                .color(orden.getColorTono())
                .material(orden.getMaterial())
                .provisional(orden.isProvisional())
                .piezasDentales(orden.getPiezasDentales())
                .estado(orden.getEstado())
                .fechaSolicitud(orden.getFechaSolicitud())
                .fechaEnvioLaboratorio(orden.getFechaEnvioLaboratorio())
                .fechaEstimadaEntrega(orden.getFechaEstimadaEntrega())
                .fechaRecepcionClinica(orden.getFechaRecepcionClinica())
                .fechaInstalacion(orden.getFechaInstalacion())
                .guiaRemision(orden.getGuiaRemision())
                .indicacionesTecnicas(orden.getIndicacionesTecnicas())
                .observaciones(orden.getObservaciones())
                .registradoPor(orden.getRegistradoPor())
                .creadoEn(orden.getCreadoEn())
                .actualizadoEn(orden.getActualizadoEn())
                .historial(historialDTO)
                .pruebas(pruebasDTO);
        if (incluirFinanzas) {
            java.math.BigDecimal pagado = montoPagado != null ? montoPagado : java.math.BigDecimal.ZERO;
            EstadoPagoLaboratorio estadoPago = EstadoPagoLaboratorio.derivar(orden.getCostoLaboratorio(), pagado);
            dto.costoLaboratorio(orden.getCostoLaboratorio())
                    .precioPaciente(orden.getPrecioPaciente())
                    .estadoPago(estadoPago != null ? estadoPago.name() : null)
                    .montoPagado(pagado)
                    .saldoLaboratorio(CuentaLaboratorio.saldo(orden.getCostoLaboratorio(), pagado))
                    .ganancia(orden.getPrecioPaciente() != null && orden.getCostoLaboratorio() != null
                            ? orden.getPrecioPaciente().subtract(orden.getCostoLaboratorio()) : null);
        }
        return dto.build();
    }

    private Map<UUID, java.math.BigDecimal> pagosPorOrden(List<UUID> ordenIds) {
        if (ordenIds.isEmpty()) {
            return Map.of();
        }
        return pagoLaboratorioRepository.findByOrdenIdInOrderByPagadoEnAsc(ordenIds).stream()
                .filter(p -> !Boolean.TRUE.equals(p.getAnulado()))
                .collect(Collectors.groupingBy(PagoLaboratorioEntity::getOrdenId,
                        Collectors.mapping(PagoLaboratorioEntity::getMonto,
                                Collectors.reducing(java.math.BigDecimal.ZERO, java.math.BigDecimal::add))));
    }

    private java.math.BigDecimal montoPagado(UUID ordenId) {
        return pagoLaboratorioRepository.findByOrdenIdOrderByPagadoEnAsc(ordenId).stream()
                .filter(p -> !Boolean.TRUE.equals(p.getAnulado()))
                .map(PagoLaboratorioEntity::getMonto)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
    }

    private PagoLaboratorioResponseDTO toPagoLaboratorioDTO(PagoLaboratorioEntity pago) {
        return PagoLaboratorioResponseDTO.builder()
                .id(pago.getId())
                .ordenId(pago.getOrdenId())
                .laboratorioId(pago.getLaboratorioId())
                .sedeId(pago.getSedeId())
                .monto(pago.getMonto())
                .metodoPago(pago.getMetodoPago())
                .numeroOperacion(pago.getNumeroOperacion())
                .pagadoEn(pago.getPagadoEn())
                .cajaSesionId(pago.getCajaSesionId())
                .registradoPor(pago.getRegistradoPor())
                .notas(pago.getNotas())
                .anulado(Boolean.TRUE.equals(pago.getAnulado()))
                .anuladoEn(pago.getAnuladoEn())
                .anuladoPor(pago.getAnuladoPor())
                .motivoAnulacion(pago.getMotivoAnulacion())
                .build();
    }

    private void validarAccesoFinanciero(ClinicaUserPrincipal user) {
        if (user == null) {
            throw new org.springframework.security.access.AccessDeniedException("No autenticado");
        }
        if (!user.tieneAlgunRol("ADMIN_GENERAL", "ADMIN_SEDE", "RECEPCION")) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Los costos del laboratorio son información administrativa.");
        }
        if (!"ADMIN_GENERAL".equalsIgnoreCase(user.rol())
                && (user.sedeId() == null || user.sedeId().isBlank())) {
            throw new org.springframework.security.access.AccessDeniedException("El usuario no tiene una sede asignada.");
        }
    }

    private static String limpiar(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private void registrarAuditoria(ClinicaUserPrincipal user, String sedeId, String accion, String detalle) {
        try {
            auditoriaRepository.save(AuditoriaLogEntity.builder()
                    .usuarioId(user != null ? user.id() : null)
                    .usuarioNombre(user != null ? user.nombre() : "Sistema")
                    .rol(user != null ? user.rol() : "ANONYMOUS")
                    .sedeId(sedeId)
                    .modulo("LABORATORIO")
                    .accion(accion)
                    .tabla("ordenes_laboratorio")
                    .detalle(detalle)
                    .ip(SecurityUtils.getClientIp())
                    .build());
        } catch (Exception e) {
            log.warn("No se pudo registrar la auditoría de laboratorio: {}", e.getMessage());
        }
    }
}
