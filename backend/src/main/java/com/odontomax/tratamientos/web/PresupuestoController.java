package com.odontomax.tratamientos.web;

import com.odontomax.tratamientos.dto.PresupuestoAceptarRequestDTO;
import com.odontomax.tratamientos.dto.PresupuestoCreateRequestDTO;
import com.odontomax.tratamientos.dto.PresupuestoPendienteDTO;
import com.odontomax.tratamientos.dto.PresupuestoResponseDTO;
import com.odontomax.tratamientos.service.PresupuestoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/presupuestos")
@RequiredArgsConstructor
public class PresupuestoController {

    private final PresupuestoService presupuestoService;

    @GetMapping("/paciente/{pacienteId}")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO', 'RECEPCION')")
    public ResponseEntity<List<PresupuestoResponseDTO>> listarPorPaciente(@PathVariable UUID pacienteId) {
        return ResponseEntity.ok(presupuestoService.listarPorPaciente(pacienteId));
    }

    @GetMapping("/pendientes")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO')")
    public ResponseEntity<List<PresupuestoPendienteDTO>> pendientes(@RequestParam String sedeId) {
        return ResponseEntity.ok(presupuestoService.listarPendientes(sedeId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO')")
    public ResponseEntity<PresupuestoResponseDTO> crear(@Valid @RequestBody PresupuestoCreateRequestDTO req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(presupuestoService.crear(req));
    }

    @PostMapping("/{id}/aceptar")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO')")
    public ResponseEntity<PresupuestoResponseDTO> aceptar(@PathVariable UUID id,
                                                          @Valid @RequestBody(required = false) PresupuestoAceptarRequestDTO req) {
        return ResponseEntity.ok(presupuestoService.aceptar(id, req));
    }

    @PostMapping("/{id}/rechazar")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO')")
    public ResponseEntity<PresupuestoResponseDTO> rechazar(@PathVariable UUID id) {
        return ResponseEntity.ok(presupuestoService.rechazar(id));
    }
}
