package com.odontomax.presentation.controller;

import com.odontomax.application.dto.response.ReporteDiarioCajaDTO;
import com.odontomax.application.service.ReporteDiarioCajaService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/caja/reporte-diario")
@RequiredArgsConstructor
public class ReporteDiarioCajaController {

    private final ReporteDiarioCajaService reporteDiario;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'RECEPCION')")
    public ResponseEntity<ReporteDiarioCajaDTO> obtener(
            @RequestParam String sedeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return ResponseEntity.ok(reporteDiario.obtener(sedeId, fecha));
    }
}
