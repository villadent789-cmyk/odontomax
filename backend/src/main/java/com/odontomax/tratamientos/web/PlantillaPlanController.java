package com.odontomax.tratamientos.web;

import com.odontomax.tratamientos.dto.PlantillaPlanDTO;
import com.odontomax.tratamientos.service.PlantillaPlanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/plantillas-plan")
@RequiredArgsConstructor
public class PlantillaPlanController {

    private final PlantillaPlanService plantillaPlanService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO')")
    public ResponseEntity<List<PlantillaPlanDTO>> listar() {
        return ResponseEntity.ok(plantillaPlanService.listar());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO')")
    public ResponseEntity<PlantillaPlanDTO> crear(@Valid @RequestBody PlantillaPlanDTO req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(plantillaPlanService.crear(req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE')")
    public ResponseEntity<Void> quitar(@PathVariable UUID id) {
        plantillaPlanService.quitar(id);
        return ResponseEntity.noContent().build();
    }
}
