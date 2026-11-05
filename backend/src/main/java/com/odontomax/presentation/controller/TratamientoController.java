package com.odontomax.presentation.controller;

import com.odontomax.application.dto.request.TratamientoAceptacionRequestDTO;
import com.odontomax.application.dto.request.TratamientoCreateRequestDTO;
import com.odontomax.application.dto.request.TratamientoUpdateRequestDTO;
import com.odontomax.application.dto.response.TratamientoResponseDTO;
import com.odontomax.identidad.api.SecurityUtils;
import com.odontomax.tratamientos.service.TratamientoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/tratamientos")
@RequiredArgsConstructor
public class TratamientoController {

    private final TratamientoService tratamientoService;

    @GetMapping("/paciente/{pacienteId}")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO', 'RECEPCION')")
    public ResponseEntity<List<TratamientoResponseDTO>> listarPorPaciente(@PathVariable UUID pacienteId) {
        return ResponseEntity.ok(tratamientoService.listarPorPaciente(pacienteId));
    }

    @GetMapping("/sede/{sedeId}")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO', 'RECEPCION')")
    public ResponseEntity<List<TratamientoResponseDTO>> listarPorSede(@PathVariable String sedeId) {
        SecurityUtils.validarAccesoSede(sedeId);
        return ResponseEntity.ok(tratamientoService.listarPorSede(sedeId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO', 'RECEPCION')")
    public ResponseEntity<TratamientoResponseDTO> obtenerPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(tratamientoService.obtenerPorId(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO')")
    public ResponseEntity<TratamientoResponseDTO> crear(@Valid @RequestBody TratamientoCreateRequestDTO dto) {
        SecurityUtils.validarAccesoSede(dto.getSedeId());
        TratamientoResponseDTO creado = tratamientoService.crear(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO')")
    public ResponseEntity<TratamientoResponseDTO> actualizar(
            @PathVariable UUID id,
            @Valid @RequestBody TratamientoUpdateRequestDTO dto
    ) {
        return ResponseEntity.ok(tratamientoService.actualizar(id, dto));
    }

    @PostMapping("/{id}/aceptar")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO', 'RECEPCION')")
    public ResponseEntity<TratamientoResponseDTO> aceptarPresupuesto(
            @PathVariable UUID id,
            @Valid @RequestBody TratamientoAceptacionRequestDTO dto
    ) {
        return ResponseEntity.ok(tratamientoService.aceptarPresupuesto(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE')")
    public ResponseEntity<Void> eliminar(@PathVariable UUID id) {
        tratamientoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
