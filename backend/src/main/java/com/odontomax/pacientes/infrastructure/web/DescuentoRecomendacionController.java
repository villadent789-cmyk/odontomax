package com.odontomax.pacientes.infrastructure.web;

import com.odontomax.pacientes.application.dto.request.AplicarDescuentoRecomendacionRequestDTO;
import com.odontomax.pacientes.application.dto.response.DescuentoRecomendacionResponseDTO;
import com.odontomax.pacientes.application.port.in.GestionarDescuentosRecomendacionUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/pacientes/{pacienteId}/descuentos-recomendacion")
@RequiredArgsConstructor
public class DescuentoRecomendacionController {

    private final GestionarDescuentosRecomendacionUseCase descuentosUseCase;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO', 'RECEPCION')")
    public ResponseEntity<List<DescuentoRecomendacionResponseDTO>> listar(@PathVariable UUID pacienteId) {
        return ResponseEntity.ok(descuentosUseCase.listar(pacienteId));
    }

    @PostMapping("/{descuentoId}/aplicar")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO')")
    public ResponseEntity<DescuentoRecomendacionResponseDTO> aplicar(
            @PathVariable UUID pacienteId,
            @PathVariable UUID descuentoId,
            @RequestBody(required = false) AplicarDescuentoRecomendacionRequestDTO req
    ) {
        return ResponseEntity.ok(descuentosUseCase.aplicar(pacienteId, descuentoId, req != null ? req.getTratamientoId() : null));
    }
}
