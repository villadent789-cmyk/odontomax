package com.odontomax.laboratorio.infrastructure.web;

import com.odontomax.laboratorio.dto.LaboratorioExternoRequestDTO;
import com.odontomax.laboratorio.dto.OrdenLaboratorioCambioEstadoRequestDTO;
import com.odontomax.laboratorio.dto.OrdenLaboratorioCreateRequestDTO;
import com.odontomax.laboratorio.dto.OrdenLaboratorioUpdateRequestDTO;
import com.odontomax.laboratorio.dto.LaboratorioExternoResponseDTO;
import com.odontomax.laboratorio.dto.LaboratorioCuentaResponseDTO;
import com.odontomax.laboratorio.dto.OrdenLaboratorioResponseDTO;
import com.odontomax.laboratorio.dto.PagoLaboratorioResponseDTO;
import com.odontomax.laboratorio.dto.AnularPagoLaboratorioRequestDTO;
import com.odontomax.laboratorio.dto.RegistrarPagoLaboratorioRequestDTO;
import com.odontomax.laboratorio.service.LaboratorioService;
import com.odontomax.identidad.api.ClinicaUserPrincipal;
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
@RequestMapping("/laboratorio")
@RequiredArgsConstructor
public class LaboratorioController {

    private final LaboratorioService laboratorioService;

    // =========================================================================
    // Órdenes de Prótesis y Laboratorio Dental
    // =========================================================================

    @GetMapping("/ordenes")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO', 'RECEPCION')")
    public ResponseEntity<List<OrdenLaboratorioResponseDTO>> listarOrdenes(
            @RequestParam(required = false) String sedeId,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) UUID pacienteId,
            @RequestParam(required = false) UUID especialistaId
    ) {
        ClinicaUserPrincipal user = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(laboratorioService.listarOrdenes(sedeId, estado, pacienteId, especialistaId, user));
    }

    @GetMapping("/ordenes/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO', 'RECEPCION')")
    public ResponseEntity<OrdenLaboratorioResponseDTO> obtenerOrden(@PathVariable UUID id) {
        ClinicaUserPrincipal user = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(laboratorioService.obtenerOrdenPorId(id, user));
    }

    @PostMapping("/ordenes")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO', 'RECEPCION')")
    public ResponseEntity<OrdenLaboratorioResponseDTO> crearOrden(
            @Valid @RequestBody OrdenLaboratorioCreateRequestDTO req
    ) {
        ClinicaUserPrincipal user = SecurityUtils.getCurrentUser();
        return ResponseEntity.status(HttpStatus.CREATED).body(laboratorioService.crearOrden(req, user));
    }

    @PutMapping("/ordenes/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO')")
    public ResponseEntity<OrdenLaboratorioResponseDTO> actualizarOrden(
            @PathVariable UUID id,
            @Valid @RequestBody OrdenLaboratorioUpdateRequestDTO req
    ) {
        ClinicaUserPrincipal user = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(laboratorioService.actualizarOrden(id, req, user));
    }

    @PatchMapping("/ordenes/{id}/estado")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO', 'RECEPCION')")
    public ResponseEntity<OrdenLaboratorioResponseDTO> cambiarEstado(
            @PathVariable UUID id,
            @Valid @RequestBody OrdenLaboratorioCambioEstadoRequestDTO req
    ) {
        ClinicaUserPrincipal user = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(laboratorioService.cambiarEstado(id, req, user));
    }

    @PostMapping("/ordenes/{id}/pruebas")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO', 'RECEPCION')")
    public ResponseEntity<OrdenLaboratorioResponseDTO> registrarPrueba(
            @PathVariable UUID id,
            @Valid @RequestBody com.odontomax.laboratorio.dto.RegistrarPruebaLaboratorioRequestDTO req
    ) {
        ClinicaUserPrincipal user = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(laboratorioService.registrarPrueba(id, req, user));
    }

    @PostMapping("/ordenes/{id}/pagos")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'RECEPCION')")
    public ResponseEntity<PagoLaboratorioResponseDTO> registrarPagoLaboratorio(
            @PathVariable UUID id,
            @Valid @RequestBody RegistrarPagoLaboratorioRequestDTO req
    ) {
        ClinicaUserPrincipal user = SecurityUtils.getCurrentUser();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(laboratorioService.registrarPagoLaboratorio(id, req, user));
    }

    @GetMapping("/ordenes/{id}/pagos")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'RECEPCION')")
    public ResponseEntity<List<PagoLaboratorioResponseDTO>> listarPagosLaboratorio(@PathVariable UUID id) {
        ClinicaUserPrincipal user = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(laboratorioService.listarPagosLaboratorio(id, user));
    }

    @PostMapping("/pagos/{id}/anular")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE')")
    public ResponseEntity<PagoLaboratorioResponseDTO> anularPagoLaboratorio(
            @PathVariable UUID id,
            @Valid @RequestBody AnularPagoLaboratorioRequestDTO req
    ) {
        ClinicaUserPrincipal user = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(laboratorioService.anularPagoLaboratorio(id, req, user));
    }

    @GetMapping("/cuentas")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'RECEPCION')")
    public ResponseEntity<List<LaboratorioCuentaResponseDTO>> listarCuentasLaboratorios(
            @RequestParam(required = false) String sedeId,
            @RequestParam(required = false) UUID laboratorioId
    ) {
        ClinicaUserPrincipal user = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(laboratorioService.listarCuentasLaboratorios(sedeId, laboratorioId, user));
    }

    // =========================================================================
    // Catálogo de Laboratorios Dentales Externos
    // =========================================================================

    @GetMapping("/proveedores")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO', 'RECEPCION')")
    public ResponseEntity<List<LaboratorioExternoResponseDTO>> listarProveedores(
            @RequestParam(required = false) Boolean soloActivos
    ) {
        return ResponseEntity.ok(laboratorioService.listarLaboratoriosExternos(soloActivos));
    }

    @PostMapping("/proveedores")
    @PreAuthorize("hasRole('ADMIN_GENERAL')")
    public ResponseEntity<LaboratorioExternoResponseDTO> crearProveedor(
            @Valid @RequestBody LaboratorioExternoRequestDTO req
    ) {
        ClinicaUserPrincipal user = SecurityUtils.getCurrentUser();
        return ResponseEntity.status(HttpStatus.CREATED).body(laboratorioService.crearLaboratorioExterno(req, user));
    }

    @PutMapping("/proveedores/{id}")
    @PreAuthorize("hasRole('ADMIN_GENERAL')")
    public ResponseEntity<LaboratorioExternoResponseDTO> actualizarProveedor(
            @PathVariable UUID id,
            @Valid @RequestBody LaboratorioExternoRequestDTO req
    ) {
        ClinicaUserPrincipal user = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(laboratorioService.actualizarLaboratorioExterno(id, req, user));
    }
}
