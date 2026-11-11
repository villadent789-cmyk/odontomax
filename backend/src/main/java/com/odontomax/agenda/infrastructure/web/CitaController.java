package com.odontomax.agenda.infrastructure.web;

import com.odontomax.agenda.application.dto.request.CitaCancelarRequestDTO;
import com.odontomax.agenda.application.dto.request.CitaReprogramarRequestDTO;
import com.odontomax.agenda.application.dto.request.CitaRequestDTO;
import com.odontomax.agenda.application.dto.request.RegistrarAdelantoRequestDTO;
import com.odontomax.agenda.application.dto.request.RegistrarContactoRequestDTO;
import com.odontomax.agenda.application.dto.response.BandejaTareasResponseDTO;
import com.odontomax.agenda.application.dto.response.CitaResponseDTO;
import com.odontomax.agenda.application.port.in.*;
import com.odontomax.agenda.application.service.CitaApplicationService;
import com.odontomax.identidad.api.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/citas")
@RequiredArgsConstructor
public class CitaController {

    private final AgendarCitaUseCase agendarCitaUseCase;
    private final ReprogramarCitaUseCase reprogramarCitaUseCase;
    private final CancelarCitaUseCase cancelarCitaUseCase;
    private final CambiarEstadoCitaUseCase cambiarEstadoCitaUseCase;
    private final IniciarAtencionCitaUseCase iniciarAtencionCitaUseCase;
    private final ConsultarCitaUseCase consultarCitaUseCase;
    private final RegistrarAdelantoUseCase registrarAdelantoUseCase;
    private final RegistrarContactoUseCase registrarContactoUseCase;
    private final ConsultarBandejaTareasUseCase consultarBandejaTareasUseCase;
    private final ProcesarExpiracionUseCase procesarExpiracionUseCase;

    private static final Set<String> ESTADOS_PERMITIDOS = Set.of(
            "PENDIENTE_WEB", "PENDIENTE_DE_PAGO", "CONFIRMADA_CON_ADELANTO", "PROGRAMADA",
            "CONFIRMADA", "EN_SALA", "EN_ATENCION", "ATENDIDA", "CANCELADA", "EXPIRADA", "NO_ASISTIO"
    );

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO', 'RECEPCION')")
    public ResponseEntity<List<CitaResponseDTO>> listarCitasDelDia(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) String sedeId
    ) {
        String sedeEfectiva = SecurityUtils.resolverSedeEfectiva(sedeId);
        List<CitaResponseDTO> dtos;
        if (desde != null || hasta != null) {
            dtos = consultarCitaUseCase.obtenerCitasPorRango(desde, hasta, sedeEfectiva);
        } else {
            LocalDate fechaConsulta = fecha != null ? fecha : LocalDate.now(CitaApplicationService.ZONE_LIMA);
            dtos = consultarCitaUseCase.obtenerCitasDelDia(fechaConsulta, sedeEfectiva);
        }
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/reservas-web-pendientes")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO', 'RECEPCION')")
    public ResponseEntity<List<CitaResponseDTO>> listarReservasWebPendientes(
            @RequestParam(required = false) String sedeId
    ) {
        String sedeEfectiva = SecurityUtils.resolverSedeEfectiva(sedeId);
        List<CitaResponseDTO> dtos = consultarCitaUseCase.obtenerReservasWebPendientes(sedeEfectiva);
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/lista-espera")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO', 'RECEPCION')")
    public ResponseEntity<List<CitaResponseDTO>> listarListaEspera(
            @RequestParam(required = false) String sedeId
    ) {
        String sedeEfectiva = SecurityUtils.resolverSedeEfectiva(sedeId);
        List<CitaResponseDTO> dtos = consultarCitaUseCase.obtenerListaEspera(sedeEfectiva);
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/bandeja-tareas")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'RECEPCION')")
    public ResponseEntity<BandejaTareasResponseDTO> obtenerBandejaTareas(
            @RequestParam(required = false) String sedeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha
    ) {
        String sedeEfectiva = SecurityUtils.resolverSedeEfectiva(sedeId);
        BandejaTareasResponseDTO bandeja = consultarBandejaTareasUseCase.obtenerBandejaTareas(sedeEfectiva, fecha);
        return ResponseEntity.ok(bandeja);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO', 'RECEPCION')")
    public ResponseEntity<CitaResponseDTO> agendarCita(@Valid @RequestBody CitaRequestDTO request) {
        String sedeEfectiva = SecurityUtils.resolverSedeEfectiva(request.getSedeId());
        if (sedeEfectiva == null || sedeEfectiva.isBlank()) {
            throw new IllegalArgumentException("Debe especificar una sede válida para agendar la cita.");
        }
        SecurityUtils.validarAccesoSede(sedeEfectiva);

        CitaResponseDTO nueva = agendarCitaUseCase.agendarCita(request, sedeEfectiva);
        return ResponseEntity.status(HttpStatus.CREATED).body(nueva);
    }

    @PostMapping("/{id}/adelanto")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'RECEPCION')")
    public ResponseEntity<CitaResponseDTO> registrarAdelanto(
            @PathVariable UUID id,
            @Valid @RequestBody RegistrarAdelantoRequestDTO dto
    ) {
        CitaResponseDTO citaExistente = consultarCitaUseCase.obtenerPorId(id);
        SecurityUtils.validarAccesoSede(citaExistente.getSedeId());

        CitaResponseDTO confirmada = registrarAdelantoUseCase.registrarAdelanto(id, dto);
        return ResponseEntity.ok(confirmada);
    }

    @PostMapping("/{id}/contacto")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'RECEPCION')")
    public ResponseEntity<CitaResponseDTO> registrarContacto(
            @PathVariable UUID id,
            @Valid @RequestBody RegistrarContactoRequestDTO dto
    ) {
        CitaResponseDTO citaExistente = consultarCitaUseCase.obtenerPorId(id);
        SecurityUtils.validarAccesoSede(citaExistente.getSedeId());

        CitaResponseDTO actualizada = registrarContactoUseCase.registrarContacto(id, dto);
        return ResponseEntity.ok(actualizada);
    }

    @PostMapping("/procesar-expiradas")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'RECEPCION')")
    public ResponseEntity<Map<String, Object>> procesarCitasExpiradas() {
        int total = procesarExpiracionUseCase.procesarCitasExpiradas();
        return ResponseEntity.ok(Map.of("mensaje", "Expiración procesada", "totalExpiradas", total));
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO', 'RECEPCION')")
    public ResponseEntity<CitaResponseDTO> cambiarEstado(
            @PathVariable UUID id,
            @RequestBody Map<String, String> payload
    ) {
        String nuevoEstado = payload != null ? payload.get("estado") : null;
        if (nuevoEstado == null || !ESTADOS_PERMITIDOS.contains(nuevoEstado.trim().toUpperCase())) {
            throw new IllegalArgumentException("Estado de cita no válido. Permitidos: " + ESTADOS_PERMITIDOS);
        }

        CitaResponseDTO citaExistente = consultarCitaUseCase.obtenerPorId(id);
        SecurityUtils.validarAccesoSede(citaExistente.getSedeId());

        String motivo = payload != null ? payload.get("motivo") : null;
        java.math.BigDecimal montoHonorario = null;
        String montoTexto = payload != null ? payload.get("montoHonorario") : null;
        if (montoTexto != null && !montoTexto.isBlank()) {
            try {
                montoHonorario = new java.math.BigDecimal(montoTexto.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("El monto de los honorarios no es válido.");
            }
        }
        String metodoHonorario = payload != null ? payload.get("metodoPagoHonorario") : null;
        String estadoFinal = nuevoEstado.trim().toUpperCase();
        CitaResponseDTO actualizada = (montoHonorario == null && metodoHonorario == null)
                ? cambiarEstadoCitaUseCase.cambiarEstado(id, estadoFinal, motivo)
                : cambiarEstadoCitaUseCase.cambiarEstado(id, estadoFinal, motivo, montoHonorario, metodoHonorario);
        return ResponseEntity.ok(actualizada);
    }

    @PostMapping("/{id}/iniciar-atencion")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'RECEPCION')")
    public ResponseEntity<CitaResponseDTO> iniciarAtencion(@PathVariable UUID id) {
        CitaResponseDTO citaExistente = consultarCitaUseCase.obtenerPorId(id);
        SecurityUtils.validarAccesoSede(citaExistente.getSedeId());
        return ResponseEntity.ok(iniciarAtencionCitaUseCase.iniciarAtencion(id));
    }

    @PatchMapping("/{id}/reprogramar")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'RECEPCION')")
    public ResponseEntity<CitaResponseDTO> reprogramarCita(
            @PathVariable UUID id,
            @Valid @RequestBody CitaReprogramarRequestDTO dto
    ) {
        CitaResponseDTO citaExistente = consultarCitaUseCase.obtenerPorId(id);
        SecurityUtils.validarAccesoSede(citaExistente.getSedeId());

        CitaResponseDTO reprogramada = reprogramarCitaUseCase.reprogramarCita(id, dto);
        return ResponseEntity.ok(reprogramada);
    }

    @PatchMapping("/{id}/cancelar")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'RECEPCION')")
    public ResponseEntity<CitaResponseDTO> cancelarCita(
            @PathVariable UUID id,
            @Valid @RequestBody CitaCancelarRequestDTO dto
    ) {
        CitaResponseDTO citaExistente = consultarCitaUseCase.obtenerPorId(id);
        SecurityUtils.validarAccesoSede(citaExistente.getSedeId());

        CitaResponseDTO cancelada = cancelarCitaUseCase.cancelarCita(id, dto);
        return ResponseEntity.ok(cancelada);
    }
}
