package com.odontomax.pacientes.infrastructure.web;

import com.odontomax.pacientes.application.dto.request.ResolverSolicitudCorreccionRequestDTO;
import com.odontomax.pacientes.application.dto.request.SolicitudEliminacionRequestDTO;
import com.odontomax.pacientes.application.dto.response.SolicitudEliminacionResponseDTO;
import com.odontomax.pacientes.application.port.in.GestionarSolicitudesEliminacionUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Eliminar una radiografía o un paciente: lo pide cualquier rol con acceso y lo resuelve solo el dueño. */
@RestController
@RequestMapping("/solicitudes-eliminacion")
@RequiredArgsConstructor
public class SolicitudEliminacionController {

    private final GestionarSolicitudesEliminacionUseCase solicitudes;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO', 'RECEPCION')")
    public ResponseEntity<SolicitudEliminacionResponseDTO> solicitar(@Valid @RequestBody SolicitudEliminacionRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(solicitudes.solicitar(dto));
    }

    @GetMapping("/pendientes")
    @PreAuthorize("hasRole('ADMIN_GENERAL')")
    public ResponseEntity<List<SolicitudEliminacionResponseDTO>> listarPendientes() {
        return ResponseEntity.ok(solicitudes.listarPendientes());
    }

    @PostMapping("/{solicitudId}/aprobar")
    @PreAuthorize("hasRole('ADMIN_GENERAL')")
    public ResponseEntity<SolicitudEliminacionResponseDTO> aprobar(
            @PathVariable UUID solicitudId,
            @Valid @RequestBody(required = false) ResolverSolicitudCorreccionRequestDTO dto
    ) {
        return ResponseEntity.ok(solicitudes.aprobar(solicitudId, dto));
    }

    @PostMapping("/{solicitudId}/rechazar")
    @PreAuthorize("hasRole('ADMIN_GENERAL')")
    public ResponseEntity<SolicitudEliminacionResponseDTO> rechazar(
            @PathVariable UUID solicitudId,
            @Valid @RequestBody(required = false) ResolverSolicitudCorreccionRequestDTO dto
    ) {
        return ResponseEntity.ok(solicitudes.rechazar(solicitudId, dto));
    }
}
