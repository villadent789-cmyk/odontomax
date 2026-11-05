package com.odontomax.pacientes.infrastructure.web;

import com.odontomax.pacientes.application.dto.request.AntecedentesUpdateRequestDTO;
import com.odontomax.pacientes.application.dto.response.AntecedenteHistorialResponseDTO;
import com.odontomax.pacientes.application.dto.response.PacienteResponseDTO;
import com.odontomax.pacientes.application.port.in.GestionarAntecedentesUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/antecedentes")
@RequiredArgsConstructor
public class AntecedentesController {

    private final GestionarAntecedentesUseCase antecedentesUseCase;

    @GetMapping("/pacientes/{pacienteId}/historial")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO', 'RECEPCION')")
    public ResponseEntity<List<AntecedenteHistorialResponseDTO>> obtenerHistorial(@PathVariable UUID pacienteId) {
        return ResponseEntity.ok(antecedentesUseCase.obtenerHistorial(pacienteId));
    }

    @PutMapping("/pacientes/{pacienteId}")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO')")
    public ResponseEntity<PacienteResponseDTO> actualizarAntecedentes(
            @PathVariable UUID pacienteId,
            @Valid @RequestBody AntecedentesUpdateRequestDTO dto
    ) {
        PacienteResponseDTO actualizado = antecedentesUseCase.actualizarAntecedentes(pacienteId, dto);
        return ResponseEntity.ok(actualizado);
    }
}