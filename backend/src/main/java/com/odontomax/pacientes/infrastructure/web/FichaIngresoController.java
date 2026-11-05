package com.odontomax.pacientes.infrastructure.web;

import com.odontomax.pacientes.application.dto.request.FichaIngresoDTO;
import com.odontomax.pacientes.application.port.in.GestionarFichaIngresoUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/pacientes/{pacienteId}/ficha-ingreso")
@RequiredArgsConstructor
public class FichaIngresoController {

    private final GestionarFichaIngresoUseCase fichaUseCase;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO', 'RECEPCION')")
    public ResponseEntity<FichaIngresoDTO> obtener(@PathVariable UUID pacienteId) {
        return ResponseEntity.ok(fichaUseCase.obtener(pacienteId));
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO', 'RECEPCION')")
    public ResponseEntity<FichaIngresoDTO> guardar(@PathVariable UUID pacienteId, @Valid @RequestBody FichaIngresoDTO ficha) {
        return ResponseEntity.ok(fichaUseCase.guardar(pacienteId, ficha));
    }
}
