package com.odontomax.presentation.controller;

import com.odontomax.application.dto.request.GastoAnulacionRequestDTO;
import com.odontomax.application.dto.request.GastoCreateRequestDTO;
import com.odontomax.application.dto.response.GastoResponseDTO;
import com.odontomax.application.service.GastoService;
import com.odontomax.identidad.api.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/gastos")
@RequiredArgsConstructor
public class GastoController {

    private final GastoService gastoService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE')")
    public ResponseEntity<List<GastoResponseDTO>> listarPorSede(@RequestParam String sedeId) {
        SecurityUtils.validarAccesoSede(sedeId);
        return ResponseEntity.ok(gastoService.listarPorSede(sedeId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE')")
    public ResponseEntity<GastoResponseDTO> obtenerPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(gastoService.obtenerPorId(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'RECEPCION')")
    public ResponseEntity<GastoResponseDTO> registrarGasto(@Valid @RequestBody GastoCreateRequestDTO dto) {
        if (dto.getSedeId() != null && !dto.getSedeId().isBlank()) {
            SecurityUtils.validarAccesoSede(dto.getSedeId());
        }
        GastoResponseDTO creado = gastoService.registrarGasto(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    @PostMapping("/{id}/anular")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE')")
    public ResponseEntity<GastoResponseDTO> anularGasto(
            @PathVariable UUID id,
            @Valid @RequestBody GastoAnulacionRequestDTO dto
    ) {
        return ResponseEntity.ok(gastoService.anularGasto(id, dto));
    }
}
