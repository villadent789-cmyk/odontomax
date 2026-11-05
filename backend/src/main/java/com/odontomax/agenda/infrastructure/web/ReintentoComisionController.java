package com.odontomax.agenda.infrastructure.web;

import com.odontomax.agenda.application.port.in.ReintentarComisionUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/citas")
@RequiredArgsConstructor
public class ReintentoComisionController {

    private final ReintentarComisionUseCase reintentarComision;

    /** Solo el administrador general ve las comisiones, así que solo él las reintenta. */
    @PostMapping("/{id}/reintentar-comision")
    @PreAuthorize("hasRole('ADMIN_GENERAL')")
    public ResponseEntity<Void> reintentar(@PathVariable UUID id) {
        reintentarComision.reintentar(id);
        return ResponseEntity.noContent().build();
    }
}
