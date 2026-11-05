package com.odontomax.pacientes.infrastructure.web;

import com.odontomax.pacientes.application.dto.request.ConsentimientoRequestDTO;
import com.odontomax.pacientes.application.dto.response.ConsentimientoResponseDTO;
import com.odontomax.pacientes.application.dto.response.PlantillaConsentimientoResponseDTO;
import com.odontomax.pacientes.application.port.in.GestionarConsentimientosUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/consentimientos")
@RequiredArgsConstructor
public class ConsentimientoController {

    private final GestionarConsentimientosUseCase consentimientoService;

    @GetMapping("/plantillas")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO', 'RECEPCION')")
    public ResponseEntity<List<PlantillaConsentimientoResponseDTO>> listarPlantillas() {
        return ResponseEntity.ok(consentimientoService.listarPlantillas());
    }

    @GetMapping("/pacientes/{pacienteId}")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO', 'RECEPCION')")
    public ResponseEntity<List<ConsentimientoResponseDTO>> listarPorPaciente(@PathVariable UUID pacienteId) {
        return ResponseEntity.ok(consentimientoService.listarPorPaciente(pacienteId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO', 'RECEPCION')")
    public ResponseEntity<ConsentimientoResponseDTO> registrarConsentimiento(
            @Valid @RequestBody ConsentimientoRequestDTO dto
    ) {
        ConsentimientoResponseDTO guardado = consentimientoService.registrarConsentimiento(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(guardado);
    }

    @GetMapping("/{id}/firma")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO', 'RECEPCION')")
    public ResponseEntity<byte[]> obtenerFirma(@PathVariable UUID id) {
        byte[] bytes = consentimientoService.obtenerFirmaImagen(id);
        if (bytes == null || bytes.length == 0) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"firma-" + id + ".png\"")
                .body(bytes);
    }

    @GetMapping("/{id}/pdf")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO', 'RECEPCION')")
    public ResponseEntity<byte[]> obtenerPdf(@PathVariable UUID id) {
        byte[] bytes = consentimientoService.obtenerPdf(id);
        if (bytes == null || bytes.length == 0) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"consentimiento-" + id + ".pdf\"")
                .body(bytes);
    }
}