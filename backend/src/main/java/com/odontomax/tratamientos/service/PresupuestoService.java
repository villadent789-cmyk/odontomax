package com.odontomax.tratamientos.service;

import com.odontomax.agenda.api.ConsultaEspecialistasModuloApi;
import com.odontomax.application.dto.request.TratamientoCreateRequestDTO;
import com.odontomax.application.dto.request.TratamientoItemRequestDTO;
import com.odontomax.application.dto.response.TratamientoResponseDTO;
import com.odontomax.identidad.api.ClinicaUserPrincipal;
import com.odontomax.identidad.api.SecurityUtils;
import com.odontomax.infrastructure.persistence.entity.PresupuestoEntity;
import com.odontomax.infrastructure.persistence.entity.PresupuestoItemEntity;
import com.odontomax.infrastructure.persistence.repository.SpringDataPresupuestoRepository;
import com.odontomax.pacientes.api.ConsultaPacientesModuloApi;
import com.odontomax.pacientes.api.PacienteResumen;
import com.odontomax.tratamientos.dto.PresupuestoAceptarRequestDTO;
import com.odontomax.tratamientos.dto.PresupuestoCreateRequestDTO;
import com.odontomax.tratamientos.dto.PresupuestoPendienteDTO;
import com.odontomax.tratamientos.dto.PresupuestoResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Presupuestos: lo que se puede hacer por un paciente. Cada línea es piezas × costo por pieza (con su gravedad). El paciente
 * acepta todo o solo una parte, y lo aceptado se convierte en un plan de tratamiento.
 */
@Service
@RequiredArgsConstructor
public class PresupuestoService {

    private static final Set<String> ROLES_CLINICOS = Set.of("ODONTOLOGO", "ADMIN_SEDE", "ADMIN_GENERAL");

    private final SpringDataPresupuestoRepository presupuestoRepository;
    private final ConsultaPacientesModuloApi pacientesApi;
    private final ConsultaEspecialistasModuloApi especialistasApi;
    private final TratamientoService tratamientoService;

    @Transactional(readOnly = true)
    public List<PresupuestoResponseDTO> listarPorPaciente(UUID pacienteId) {
        exigirPaciente(pacienteId);
        return presupuestoRepository.findByPacienteIdOrderByCreadoEnDesc(pacienteId).stream().map(this::aDto).toList();
    }

    /** Presupuestos vigentes de una sede (los 30 más recientes), con el nombre del paciente. */
    @Transactional(readOnly = true)
    public List<PresupuestoPendienteDTO> listarPendientes(String sedeId) {
        SecurityUtils.validarAccesoSede(sedeId);
        List<PresupuestoEntity> vigentes = presupuestoRepository.findTop30BySedeIdAndEstadoOrderByCreadoEnDesc(sedeId, "VIGENTE");
        var pacientes = pacientesApi.findAllById(vigentes.stream().map(PresupuestoEntity::getPacienteId).collect(Collectors.toSet()));
        return vigentes.stream().map(p -> {
            PacienteResumen paciente = pacientes.get(p.getPacienteId());
            return PresupuestoPendienteDTO.builder()
                    .id(p.getId())
                    .pacienteId(p.getPacienteId())
                    .pacienteNombre(paciente == null ? null : (paciente.nombres() + " " + paciente.apellidos()).trim())
                    .especialistaNombre(p.getEspecialistaId() == null ? null : especialistasApi.findNombreById(p.getEspecialistaId()).orElse(null))
                    .motivo(p.getMotivo())
                    .total(p.getTotal())
                    .lineas(p.getItems().size())
                    .creadoEn(p.getCreadoEn())
                    .build();
        }).toList();
    }

    @Transactional
    public PresupuestoResponseDTO crear(PresupuestoCreateRequestDTO req) {
        ClinicaUserPrincipal user = exigirClinico();
        SecurityUtils.validarAccesoSede(req.getSedeId());
        exigirPaciente(req.getPacienteId());

        PresupuestoEntity presupuesto = PresupuestoEntity.builder()
                .pacienteId(req.getPacienteId())
                .sedeId(req.getSedeId())
                .especialistaId(req.getEspecialistaId())
                .motivo(limpio(req.getMotivo()))
                .notas(limpio(req.getNotas()))
                .creadoPor(user.nombre())
                .build();
        BigDecimal total = BigDecimal.ZERO;
        for (PresupuestoCreateRequestDTO.Item it : req.getItems()) {
            int cantidad = it.getCantidad() != null ? it.getCantidad() : 1;
            presupuesto.getItems().add(PresupuestoItemEntity.builder()
                    .presupuesto(presupuesto)
                    .procedimientoId(limpio(it.getProcedimientoId()))
                    .nombre(it.getNombre().trim())
                    .piezaFdi(it.getPiezaFdi())
                    .gravedad(it.getGravedad())
                    .cantidad(cantidad)
                    .precioUnitario(it.getPrecioUnitario())
                    .build());
            total = total.add(it.getPrecioUnitario().multiply(BigDecimal.valueOf(cantidad)));
        }
        presupuesto.setTotal(total);
        return aDto(presupuestoRepository.save(presupuesto));
    }

    /** El paciente acepta (todo o parte): se crea el plan con las líneas aceptadas y el presupuesto queda ACEPTADO. */
    @Transactional
    public PresupuestoResponseDTO aceptar(UUID id, PresupuestoAceptarRequestDTO req) {
        exigirClinico();
        PresupuestoEntity presupuesto = vigente(id);
        Set<UUID> elegidas = req != null && req.getItemIds() != null && !req.getItemIds().isEmpty()
                ? Set.copyOf(req.getItemIds()) : null;
        if (elegidas != null) {
            Set<UUID> existentes = presupuesto.getItems().stream().map(PresupuestoItemEntity::getId).collect(Collectors.toSet());
            if (!existentes.containsAll(elegidas)) {
                throw new IllegalArgumentException("Alguna de las líneas elegidas no pertenece a este presupuesto.");
            }
        }
        List<PresupuestoItemEntity> aceptadas = presupuesto.getItems().stream()
                .filter(i -> elegidas == null || elegidas.contains(i.getId()))
                .toList();
        if (aceptadas.isEmpty()) {
            throw new IllegalArgumentException("Elige al menos un tratamiento que el paciente haya aceptado.");
        }
        UUID especialistaId = req != null && req.getEspecialistaId() != null ? req.getEspecialistaId() : presupuesto.getEspecialistaId();
        if (especialistaId == null) {
            throw new IllegalArgumentException("Indica el doctor responsable del plan.");
        }

        BigDecimal totalAceptado = BigDecimal.ZERO;
        List<TratamientoItemRequestDTO> itemsPlan = new java.util.ArrayList<>();
        for (PresupuestoItemEntity i : aceptadas) {
            totalAceptado = totalAceptado.add(i.getPrecioUnitario().multiply(BigDecimal.valueOf(i.getCantidad())));
            itemsPlan.add(TratamientoItemRequestDTO.builder()
                    .procedimientoId(i.getProcedimientoId())
                    .nombreProcedimiento(i.getNombre())
                    .piezaFdi(i.getPiezaFdi())
                    .precioBase(i.getPrecioUnitario())
                    .cantidad(i.getCantidad())
                    .gravedad(i.getGravedad())
                    .build());
        }
        String nombrePlan = aceptadas.stream().map(PresupuestoItemEntity::getNombre).distinct().collect(Collectors.joining(" + "));
        TratamientoResponseDTO plan = tratamientoService.crear(TratamientoCreateRequestDTO.builder()
                .pacienteId(presupuesto.getPacienteId())
                .sedeId(presupuesto.getSedeId())
                .especialistaId(especialistaId)
                .nombre(nombrePlan.length() > 180 ? nombrePlan.substring(0, 177) + "…" : nombrePlan)
                .costoTotal(totalAceptado)
                .totalSesiones(req != null && req.getTotalSesiones() != null ? req.getTotalSesiones() : 1)
                .observaciones("Plan creado desde un presupuesto" + (presupuesto.getMotivo() != null ? ": " + presupuesto.getMotivo() : ""))
                .items(itemsPlan)
                .build());

        aceptadas.forEach(i -> i.setAceptado(true));
        presupuesto.setEstado("ACEPTADO");
        presupuesto.setTratamientoId(plan.getId());
        presupuesto.setResueltoEn(Instant.now());
        return aDto(presupuestoRepository.save(presupuesto));
    }

    @Transactional
    public PresupuestoResponseDTO rechazar(UUID id) {
        exigirClinico();
        PresupuestoEntity presupuesto = vigente(id);
        presupuesto.setEstado("RECHAZADO");
        presupuesto.setResueltoEn(Instant.now());
        return aDto(presupuestoRepository.save(presupuesto));
    }

    private PresupuestoEntity vigente(UUID id) {
        PresupuestoEntity presupuesto = presupuestoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Presupuesto no encontrado"));
        SecurityUtils.validarAccesoSede(presupuesto.getSedeId());
        especialistasApi.exigirAccesoDeOdontologo(presupuesto.getPacienteId());
        if (!"VIGENTE".equals(presupuesto.getEstado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este presupuesto ya fue " + presupuesto.getEstado().toLowerCase() + ".");
        }
        return presupuesto;
    }

    private PacienteResumen exigirPaciente(UUID pacienteId) {
        especialistasApi.exigirAccesoDeOdontologo(pacienteId);
        PacienteResumen paciente = pacientesApi.findById(pacienteId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el paciente con ID: " + pacienteId));
        SecurityUtils.validarAccesoSede(paciente.sedeId());
        return paciente;
    }

    private ClinicaUserPrincipal exigirClinico() {
        ClinicaUserPrincipal user = SecurityUtils.getCurrentUser();
        if (user == null || user.roles().stream().noneMatch(ROLES_CLINICOS::contains)) {
            throw new AccessDeniedException("Solo un odontólogo o un administrador registra y resuelve presupuestos.");
        }
        return user;
    }

    private static String limpio(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }

    private PresupuestoResponseDTO aDto(PresupuestoEntity p) {
        BigDecimal aceptado = p.getItems().stream().filter(PresupuestoItemEntity::isAceptado)
                .map(i -> i.getPrecioUnitario().multiply(BigDecimal.valueOf(i.getCantidad())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return PresupuestoResponseDTO.builder()
                .id(p.getId())
                .pacienteId(p.getPacienteId())
                .sedeId(p.getSedeId())
                .especialistaId(p.getEspecialistaId())
                .especialistaNombre(p.getEspecialistaId() == null ? null : especialistasApi.findNombreById(p.getEspecialistaId()).orElse(null))
                .motivo(p.getMotivo())
                .notas(p.getNotas())
                .estado(p.getEstado())
                .total(p.getTotal())
                .totalAceptado(aceptado)
                .tratamientoId(p.getTratamientoId())
                .creadoPor(p.getCreadoPor())
                .creadoEn(p.getCreadoEn())
                .resueltoEn(p.getResueltoEn())
                .items(p.getItems().stream().map(i -> PresupuestoResponseDTO.Item.builder()
                        .id(i.getId())
                        .procedimientoId(i.getProcedimientoId())
                        .nombre(i.getNombre())
                        .piezaFdi(i.getPiezaFdi())
                        .gravedad(i.getGravedad())
                        .cantidad(i.getCantidad())
                        .precioUnitario(i.getPrecioUnitario())
                        .subtotal(i.getPrecioUnitario().multiply(BigDecimal.valueOf(i.getCantidad())))
                        .aceptado(i.isAceptado())
                        .build()).toList())
                .build();
    }
}
