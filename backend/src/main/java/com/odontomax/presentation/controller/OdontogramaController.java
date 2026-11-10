package com.odontomax.presentation.controller;

import com.odontomax.application.dto.request.OdontogramaLoteRequestDTO;
import com.odontomax.application.dto.request.OdontogramaPiezaRequestDTO;
import com.odontomax.application.dto.response.OdontogramaRegistroResponseDTO;
import com.odontomax.application.service.OdontogramaService;
import com.odontomax.clinica.service.OdontogramaPdfService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/odontograma")
@RequiredArgsConstructor
public class OdontogramaController {

    private final OdontogramaService odontogramaService;
    private final OdontogramaPdfService odontogramaPdfService;

    @GetMapping("/pacientes/{pacienteId}")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO', 'RECEPCION')")
    public ResponseEntity<List<OdontogramaRegistroResponseDTO>> obtenerEstadoActual(@PathVariable UUID pacienteId) {
        return ResponseEntity.ok(odontogramaService.obtenerEstadoActual(pacienteId));
    }

    @GetMapping("/pacientes/{pacienteId}/historial")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO', 'RECEPCION')")
    public ResponseEntity<List<OdontogramaRegistroResponseDTO>> obtenerHistorial(@PathVariable UUID pacienteId) {
        return ResponseEntity.ok(odontogramaService.obtenerHistorial(pacienteId));
    }

    @PostMapping("/pacientes/{pacienteId}/pieza")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO', 'RECEPCION')")
    public ResponseEntity<OdontogramaRegistroResponseDTO> registrarPieza(
            @PathVariable UUID pacienteId,
            @Valid @RequestBody OdontogramaPiezaRequestDTO dto
    ) {
        OdontogramaRegistroResponseDTO guardado = odontogramaService.registrarPieza(pacienteId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(guardado);
    }

    @PostMapping("/pacientes/{pacienteId}/lote")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO', 'RECEPCION')")
    public ResponseEntity<List<OdontogramaRegistroResponseDTO>> registrarLote(
            @PathVariable UUID pacienteId,
            @Valid @RequestBody OdontogramaLoteRequestDTO dto
    ) {
        List<OdontogramaRegistroResponseDTO> guardados = odontogramaService.registrarLote(pacienteId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(guardados);
    }

    @GetMapping("/pacientes/{pacienteId}/pdf")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO')")
    public ResponseEntity<byte[]> exportarPdf(@PathVariable UUID pacienteId) {
        byte[] pdf = odontogramaPdfService.generarPdf(pacienteId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(
                ContentDisposition.attachment().filename("odontograma-" + pacienteId + ".pdf").build()
        );
        return ResponseEntity.ok().headers(headers).body(pdf);
    }
}
