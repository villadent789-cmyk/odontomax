package com.odontomax.reportes.infrastructure.web;

import com.odontomax.application.dto.response.*;
import com.odontomax.reportes.dto.RendimientoDoctoresResponseDTO;
import com.odontomax.reportes.service.RendimientoDoctoresService;
import com.odontomax.reportes.service.ReporteService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import com.odontomax.reportes.dto.ReporteCitasRendimientoResponseDTO;
import com.odontomax.reportes.dto.ReporteCitasResponseDTO;
import com.odontomax.reportes.dto.ReporteDeudoresResponseDTO;
import com.odontomax.reportes.dto.ReporteDoctoresResponseDTO;
import com.odontomax.reportes.dto.ReporteFinancieroResponseDTO;
import com.odontomax.reportes.dto.ReporteInventarioResponseDTO;
import com.odontomax.reportes.dto.ReportePacientesResponseDTO;

@RestController
@RequestMapping("/reportes")
@RequiredArgsConstructor
public class ReporteController {

    private final ReporteService reporteService;
    private final RendimientoDoctoresService rendimientoDoctoresService;

    @GetMapping("/financiero")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE')")
    public ResponseEntity<ReporteFinancieroResponseDTO> obtenerReporteFinanciero(
            @RequestParam(required = false) String sedeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta
    ) {
        return ResponseEntity.ok(reporteService.obtenerReporteFinanciero(sedeId, desde, hasta));
    }

    @GetMapping("/rendimiento-doctores")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE')")
    public ResponseEntity<RendimientoDoctoresResponseDTO> obtenerRendimientoDoctores(
            @RequestParam(required = false) String mes,
            @RequestParam(required = false) String sedeId
    ) {
        return ResponseEntity.ok(rendimientoDoctoresService.obtenerRendimiento(mes, sedeId));
    }

    @GetMapping("/deudores")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE')")
    public ResponseEntity<ReporteDeudoresResponseDTO> obtenerReporteDeudores(
            @RequestParam(required = false) String sedeId
    ) {
        return ResponseEntity.ok(reporteService.obtenerReporteDeudores(sedeId));
    }

    @GetMapping("/citas-rendimiento")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE')")
    public ResponseEntity<ReporteCitasRendimientoResponseDTO> obtenerReporteCitasRendimiento(
            @RequestParam(required = false) String sedeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta
    ) {
        return ResponseEntity.ok(reporteService.obtenerReporteCitasRendimiento(sedeId, desde, hasta));
    }

    @GetMapping("/citas")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE')")
    public ResponseEntity<ReporteCitasResponseDTO> obtenerReporteCitas(
            @RequestParam(required = false) String sedeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta
    ) {
        return ResponseEntity.ok(reporteService.obtenerReporteCitas(sedeId, desde, hasta));
    }

    @GetMapping("/pacientes")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE')")
    public ResponseEntity<ReportePacientesResponseDTO> obtenerReportePacientes(
            @RequestParam(required = false) String sedeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta
    ) {
        return ResponseEntity.ok(reporteService.obtenerReportePacientes(sedeId, desde, hasta));
    }

    @GetMapping("/doctores")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE')")
    public ResponseEntity<ReporteDoctoresResponseDTO> obtenerReporteDoctores(
            @RequestParam(required = false) String sedeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta
    ) {
        return ResponseEntity.ok(reporteService.obtenerReporteDoctores(sedeId, desde, hasta));
    }

    @GetMapping("/inventario")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE')")
    public ResponseEntity<ReporteInventarioResponseDTO> obtenerReporteInventario(
            @RequestParam(required = false) String sedeId
    ) {
        return ResponseEntity.ok(reporteService.obtenerReporteInventario(sedeId));
    }
}
