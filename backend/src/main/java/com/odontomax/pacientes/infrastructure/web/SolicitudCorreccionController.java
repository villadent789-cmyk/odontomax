package com.odontomax.pacientes.infrastructure.web;

import com.odontomax.pacientes.application.dto.request.ResolverSolicitudCorreccionRequestDTO;
import com.odontomax.pacientes.application.dto.request.SolicitudCorreccionRequestDTO;
import com.odontomax.pacientes.application.dto.response.SolicitudCorreccionResponseDTO;
import com.odontomax.pacientes.application.port.in.GestionarSolicitudesCorreccionUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Correcciones de nombre, DNI y fecha de nacimiento: las pide cualquier rol y las resuelve solo el dueño. */
@RestController
@RequestMapping("/pacientes")
@RequiredArgsConstructor
public class SolicitudCorreccionController {

    private final GestionarSolicitudesCorreccionUseCase solicitudes;

    @PostMapping("/{id}/solicitudes-correccion")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO', 'RECEPCION')")
    public ResponseEntity<SolicitudCorreccionResponseDTO> solicitar(
            @PathVariable UUID id,
            @Valid @RequestBody SolicitudCorreccionRequestDTO dto
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(solicitudes.solicitar(id, dto));
    }

    @GetMapping("/{id}/solicitudes-correccion")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO', 'RECEPCION')")
    public ResponseEntity<List<SolicitudCorreccionResponseDTO>> listarDePaciente(@PathVariable UUID id) {
        return ResponseEntity.ok(solicitudes.listarDePaciente(id));
    }

    @GetMapping("/solicitudes-correccion/pendientes")
    @PreAuthorize("hasRole('ADMIN_GENERAL')")
    public ResponseEntity<List<SolicitudCorreccionResponseDTO>> listarPendientes() {
        return ResponseEntity.ok(solicitudes.listarPendientes());
    }

    @PostMapping("/solicitudes-correccion/{solicitudId}/aprobar")
    @PreAuthorize("hasRole('ADMIN_GENERAL')")
    public ResponseEntity<SolicitudCorreccionResponseDTO> aprobar(
            @PathVariable UUID solicitudId,
            @Valid @RequestBody(required = false) ResolverSolicitudCorreccionRequestDTO dto
    ) {
        return ResponseEntity.ok(solicitudes.aprobar(solicitudId, dto));
    }

    @PostMapping("/solicitudes-correccion/{solicitudId}/rechazar")
    @PreAuthorize("hasRole('ADMIN_GENERAL')")
    public ResponseEntity<SolicitudCorreccionResponseDTO> rechazar(
            @PathVariable UUID solicitudId,
            @Valid @RequestBody(required = false) ResolverSolicitudCorreccionRequestDTO dto
    ) {
        return ResponseEntity.ok(solicitudes.rechazar(solicitudId, dto));
    }
}
