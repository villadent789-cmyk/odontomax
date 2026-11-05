package com.odontomax.inventario.web;

import com.odontomax.inventario.dto.AlertaConsumoDTO;
import com.odontomax.inventario.dto.RecetaInsumoDTO;
import com.odontomax.inventario.service.ConsumoInsumosService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/inventario")
@RequiredArgsConstructor
public class ConsumoInsumosController {

    private final ConsumoInsumosService consumo;

    @GetMapping("/servicios/{servicioId}/insumos")
    @PreAuthorize("hasRole('ADMIN_GENERAL')")
    public ResponseEntity<List<RecetaInsumoDTO>> receta(@PathVariable String servicioId) {
        return ResponseEntity.ok(consumo.obtenerReceta(servicioId));
    }

    @PutMapping("/servicios/{servicioId}/insumos")
    @PreAuthorize("hasRole('ADMIN_GENERAL')")
    public ResponseEntity<List<RecetaInsumoDTO>> guardarReceta(@PathVariable String servicioId,
                                                               @RequestBody @Valid List<@Valid RecetaInsumoDTO> lineas) {
        return ResponseEntity.ok(consumo.guardarReceta(servicioId, lineas));
    }

    @GetMapping("/alertas-consumo")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE')")
    public ResponseEntity<List<AlertaConsumoDTO>> alertas(@RequestParam String sedeId) {
        return ResponseEntity.ok(consumo.alertasPendientes(sedeId));
    }

    @PostMapping("/alertas-consumo/{id}/resolver")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE')")
    public ResponseEntity<Void> resolver(@PathVariable UUID id) {
        consumo.resolverAlerta(id);
        return ResponseEntity.noContent().build();
    }
}
