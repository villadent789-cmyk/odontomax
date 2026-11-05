package com.odontomax.radiologia.infrastructure.web;

import com.odontomax.radiologia.dto.ComisionRadiologicaCreateRequestDTO;
import com.odontomax.radiologia.dto.ComisionRadiologicaLiquidarRequestDTO;
import com.odontomax.radiologia.dto.DoctorDerivadorRequestDTO;
import com.odontomax.radiologia.dto.ComisionRadiologicaResponseDTO;
import com.odontomax.radiologia.dto.DoctorDerivadorResponseDTO;
import com.odontomax.application.dto.response.ResumenComisionesResponseDTO;
import com.odontomax.radiologia.service.ComisionRadiologicaService;
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
@RequestMapping("/radiologia")
@RequiredArgsConstructor
public class ComisionRadiologicaController {

    private final ComisionRadiologicaService comisionService;

    // =========================================================================
    // Catálogo de Doctores Derivadores (Clínicas Externas)
    // =========================================================================

    @GetMapping("/derivadores")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO', 'RECEPCION')")
    public ResponseEntity<List<DoctorDerivadorResponseDTO>> listarDerivadores(
            @RequestParam(required = false, defaultValue = "false") Boolean soloActivos
    ) {
        return ResponseEntity.ok(comisionService.listarDoctoresDerivadores(soloActivos));
    }

    @GetMapping("/derivadores/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO', 'RECEPCION')")
    public ResponseEntity<DoctorDerivadorResponseDTO> obtenerDerivador(@PathVariable UUID id) {
        return ResponseEntity.ok(comisionService.obtenerDoctorDerivador(id));
    }

    @PostMapping("/derivadores")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'RECEPCION')")
    public ResponseEntity<DoctorDerivadorResponseDTO> crearDerivador(
            @Valid @RequestBody DoctorDerivadorRequestDTO req
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(comisionService.crearDoctorDerivador(req));
    }

    @PutMapping("/derivadores/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'RECEPCION')")
    public ResponseEntity<DoctorDerivadorResponseDTO> actualizarDerivador(
            @PathVariable UUID id,
            @Valid @RequestBody DoctorDerivadorRequestDTO req
    ) {
        return ResponseEntity.ok(comisionService.actualizarDoctorDerivador(id, req));
    }

    // =========================================================================
    // Comisiones Radiológicas y Liquidaciones
    // =========================================================================

    @GetMapping("/comisiones")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO', 'RECEPCION')")
    public ResponseEntity<List<ComisionRadiologicaResponseDTO>> listarComisiones(
            @RequestParam(required = false) UUID doctorId,
            @RequestParam(required = false) String estado
    ) {
        return ResponseEntity.ok(comisionService.listarComisiones(doctorId, estado));
    }

    @GetMapping("/comisiones/resumen")
    @PreAuthorize("hasRole('ADMIN_GENERAL')")
    public ResponseEntity<ResumenComisionesResponseDTO> obtenerResumen() {
        return ResponseEntity.ok(comisionService.obtenerResumen());
    }

    @GetMapping("/comisiones/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO', 'RECEPCION')")
    public ResponseEntity<ComisionRadiologicaResponseDTO> obtenerComision(@PathVariable UUID id) {
        return ResponseEntity.ok(comisionService.obtenerComisionPorId(id));
    }

    @PostMapping("/comisiones")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO', 'RECEPCION')")
    public ResponseEntity<ComisionRadiologicaResponseDTO> registrarComision(
            @Valid @RequestBody ComisionRadiologicaCreateRequestDTO req
    ) {
        ClinicaUserPrincipal user = SecurityUtils.getCurrentUser();
        return ResponseEntity.status(HttpStatus.CREATED).body(comisionService.registrarComision(req, user));
    }

    @PatchMapping("/comisiones/{id}/liquidar")
    @PreAuthorize("hasRole('ADMIN_GENERAL')")
    public ResponseEntity<ComisionRadiologicaResponseDTO> liquidarComision(
            @PathVariable UUID id,
            @Valid @RequestBody ComisionRadiologicaLiquidarRequestDTO req
    ) {
        ClinicaUserPrincipal user = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(comisionService.liquidarComision(id, req, user));
    }
}
