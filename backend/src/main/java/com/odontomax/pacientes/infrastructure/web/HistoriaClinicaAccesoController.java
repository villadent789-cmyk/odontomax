package com.odontomax.pacientes.infrastructure.web;

import com.odontomax.pacientes.application.dto.response.HistoriaClinicaAccesoResponseDTO;
import com.odontomax.pacientes.application.port.in.GestionarAccesoHistoriaUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/historia-clinica")
@RequiredArgsConstructor
public class HistoriaClinicaAccesoController {

    private final GestionarAccesoHistoriaUseCase accesoService;

    @PostMapping("/pacientes/{pacienteId}/apertura")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO', 'RECEPCION')")
    public ResponseEntity<HistoriaClinicaAccesoResponseDTO> registrarApertura(@PathVariable UUID pacienteId) {
        HistoriaClinicaAccesoResponseDTO registrado = accesoService.registrarApertura(pacienteId);
        return ResponseEntity.status(HttpStatus.CREATED).body(registrado);
    }

    @GetMapping("/accesos")
    @PreAuthorize("hasRole('ADMIN_GENERAL')")
    public ResponseEntity<List<HistoriaClinicaAccesoResponseDTO>> listarAccesos(
            @RequestParam(value = "pacienteId", required = false) UUID pacienteId
    ) {
        return ResponseEntity.ok(accesoService.listarAccesos(pacienteId));
    }
}