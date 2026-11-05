package com.odontomax.presentation.controller;

import com.odontomax.application.dto.request.PagoAnulacionRequestDTO;
import com.odontomax.application.dto.request.PagoCreateRequestDTO;
import com.odontomax.application.dto.response.PagoResponseDTO;
import com.odontomax.application.service.PagoService;
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
@RequestMapping("/pagos")
@RequiredArgsConstructor
public class PagoController {

    private final PagoService pagoService;

    @GetMapping("/paciente/{pacienteId}")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO', 'RECEPCION')")
    public ResponseEntity<List<PagoResponseDTO>> listarPorPaciente(@PathVariable UUID pacienteId) {
        return ResponseEntity.ok(pagoService.listarPorPaciente(pacienteId));
    }

    @GetMapping("/sede/{sedeId}")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO', 'RECEPCION')")
    public ResponseEntity<List<PagoResponseDTO>> listarPorSede(@PathVariable String sedeId) {
        SecurityUtils.validarAccesoSede(sedeId);
        return ResponseEntity.ok(pagoService.listarPorSede(sedeId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO', 'RECEPCION')")
    public ResponseEntity<PagoResponseDTO> obtenerPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(pagoService.obtenerPorId(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO', 'RECEPCION')")
    public ResponseEntity<PagoResponseDTO> registrarPago(@Valid @RequestBody PagoCreateRequestDTO dto) {
        if (dto.getSedeId() != null && !dto.getSedeId().isBlank()) {
            SecurityUtils.validarAccesoSede(dto.getSedeId());
        }
        PagoResponseDTO creado = pagoService.registrarPago(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    @PostMapping("/{id}/anular")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE')")
    public ResponseEntity<PagoResponseDTO> anularPago(
            @PathVariable UUID id,
            @Valid @RequestBody PagoAnulacionRequestDTO dto
    ) {
        return ResponseEntity.ok(pagoService.anularPago(id, dto));
    }
}
