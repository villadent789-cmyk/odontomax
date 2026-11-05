package com.odontomax.radiologia.infrastructure.web;

import com.odontomax.radiologia.dto.ComisionFallidaResponseDTO;
import com.odontomax.radiologia.dto.ComisionLoteRequestDTO;
import com.odontomax.radiologia.dto.ComisionLoteResponseDTO;
import com.odontomax.radiologia.dto.EstadoCuentaDerivadorDTO;
import com.odontomax.radiologia.service.LiquidacionComisionesService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Pago de comisiones: solo el administrador general ve y mueve estos montos. */
@RestController
@RequestMapping("/radiologia/comisiones")
@RequiredArgsConstructor
public class LiquidacionComisionesController {

    private final LiquidacionComisionesService liquidacionService;

    @PostMapping("/liquidar-lote")
    @PreAuthorize("hasRole('ADMIN_GENERAL')")
    public ResponseEntity<ComisionLoteResponseDTO> liquidarLote(@Valid @RequestBody ComisionLoteRequestDTO req) {
        return ResponseEntity.ok(liquidacionService.liquidarLote(req));
    }

    @GetMapping("/estado-cuenta")
    @PreAuthorize("hasRole('ADMIN_GENERAL')")
    public ResponseEntity<EstadoCuentaDerivadorDTO> estadoCuenta(
            @RequestParam UUID doctorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta
    ) {
        return ResponseEntity.ok(liquidacionService.estadoCuenta(doctorId, desde, hasta));
    }

    @GetMapping("/fallidas")
    @PreAuthorize("hasRole('ADMIN_GENERAL')")
    public ResponseEntity<List<ComisionFallidaResponseDTO>> fallidas() {
        return ResponseEntity.ok(liquidacionService.listarFallidas());
    }
}
