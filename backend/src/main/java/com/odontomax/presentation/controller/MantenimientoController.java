package com.odontomax.presentation.controller;

import com.odontomax.application.dto.response.DatabaseSizeResponseDTO;
import com.odontomax.application.service.MantenimientoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/mantenimiento")
@RequiredArgsConstructor
public class MantenimientoController {

    private final MantenimientoService mantenimientoService;

    @GetMapping("/tamano-bd")
    @PreAuthorize("hasRole('ADMIN_GENERAL')")
    public ResponseEntity<DatabaseSizeResponseDTO> obtenerTamanoBaseDatos() {
        return ResponseEntity.ok(mantenimientoService.obtenerTamanoBaseDatos());
    }
}
