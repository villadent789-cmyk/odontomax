package com.odontomax.presentation.controller;

import com.odontomax.application.dto.request.CajaAperturaRequestDTO;
import com.odontomax.application.dto.request.CajaCierreRequestDTO;
import com.odontomax.application.dto.request.CajaMovimientoRequestDTO;
import com.odontomax.application.dto.response.CajaMovimientoResponseDTO;
import com.odontomax.application.dto.response.CajaSesionResponseDTO;
import com.odontomax.application.dto.response.CajaResumenDiarioDTO;
import com.odontomax.application.service.CajaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.time.LocalDate;

@RestController
@RequestMapping("/caja")
@RequiredArgsConstructor
public class CajaController {

    private final CajaService cajaService;

    @GetMapping("/activa")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO', 'RECEPCION')")
    public ResponseEntity<CajaSesionResponseDTO> obtenerSesionActiva(@RequestParam(required = false) String sedeId) {
        return cajaService.obtenerSesionActiva(sedeId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @GetMapping("/historial")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'RECEPCION')")
    public ResponseEntity<List<CajaSesionResponseDTO>> listarSesionesPorSede(@RequestParam String sedeId) {
        return ResponseEntity.ok(cajaService.listarSesionesPorSede(sedeId));
    }

    @GetMapping("/resumen-diario")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'RECEPCION')")
    public ResponseEntity<CajaResumenDiarioDTO> obtenerResumenDiario(
            @RequestParam String sedeId,
            @RequestParam(required = false) LocalDate fecha) {
        return ResponseEntity.ok(cajaService.obtenerResumenDiario(sedeId, fecha));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'RECEPCION')")
    public ResponseEntity<CajaSesionResponseDTO> obtenerPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(cajaService.obtenerPorId(id));
    }

    @PostMapping("/abrir")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'RECEPCION')")
    public ResponseEntity<CajaSesionResponseDTO> abrirCaja(@Valid @RequestBody CajaAperturaRequestDTO dto) {
        CajaSesionResponseDTO creada = cajaService.abrirCaja(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }

    @PostMapping("/{id}/movimientos")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'RECEPCION')")
    public ResponseEntity<CajaMovimientoResponseDTO> registrarMovimientoManual(
            @PathVariable UUID id,
            @Valid @RequestBody CajaMovimientoRequestDTO dto
    ) {
        CajaMovimientoResponseDTO mov = cajaService.registrarMovimientoManual(id, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(mov);
    }

    @PostMapping("/{id}/cerrar")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'RECEPCION')")
    public ResponseEntity<CajaSesionResponseDTO> cerrarCaja(
            @PathVariable UUID id,
            @Valid @RequestBody CajaCierreRequestDTO dto
    ) {
        return ResponseEntity.ok(cajaService.cerrarCaja(id, dto));
    }
}
