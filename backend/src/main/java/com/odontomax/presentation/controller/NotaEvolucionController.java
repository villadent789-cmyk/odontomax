package com.odontomax.presentation.controller;

import com.odontomax.application.dto.request.NotaEvolucionRequestDTO;
import com.odontomax.application.dto.response.NotaEvolucionResponseDTO;
import com.odontomax.application.service.NotaEvolucionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/evoluciones")
@RequiredArgsConstructor
public class NotaEvolucionController {

    private final NotaEvolucionService notaEvolucionService;

    @GetMapping("/pacientes/{pacienteId}")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO')")
    public ResponseEntity<List<NotaEvolucionResponseDTO>> listarPorPaciente(@PathVariable UUID pacienteId) {
        return ResponseEntity.ok(notaEvolucionService.listarPorPaciente(pacienteId));
    }

    @PostMapping("/pacientes/{pacienteId}")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO')")
    public ResponseEntity<NotaEvolucionResponseDTO> crearNota(
            @PathVariable UUID pacienteId,
            @Valid @RequestBody NotaEvolucionRequestDTO dto
    ) {
        NotaEvolucionResponseDTO guardada = notaEvolucionService.crearNota(pacienteId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(guardada);
    }
}
