package com.odontomax.reportes.infrastructure.web;

import com.odontomax.reportes.dto.MiAvanceResponseDTO;
import com.odontomax.reportes.service.RendimientoDoctoresService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rendimiento")
@RequiredArgsConstructor
public class RendimientoController {

    private final RendimientoDoctoresService rendimientoService;

    /** Avance del mes del odontólogo autenticado; nunca incluye comisión. */
    @GetMapping("/mi-avance")
    @PreAuthorize("hasRole('ODONTOLOGO')")
    public ResponseEntity<MiAvanceResponseDTO> miAvance(@RequestParam(required = false) String mes) {
        return ResponseEntity.ok(rendimientoService.obtenerMiAvance(mes));
    }
}
