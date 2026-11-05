package com.odontomax.sedes.infrastructure.web;

import com.odontomax.sedes.dto.SedeRequestDTO;
import com.odontomax.sedes.dto.SedeResponseDTO;
import com.odontomax.sedes.service.SedeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/sedes")
@RequiredArgsConstructor
public class SedeController {

    private final SedeService sedeService;

    /** Público (permitAll en SecurityConfig): solo sedes activas, para la web y los selectores. */
    @GetMapping
    public ResponseEntity<List<SedeResponseDTO>> listarActivas(@RequestParam(value = "negocio", required = false) String negocio) {
        if (negocio != null && !negocio.isBlank()) {
            return ResponseEntity.ok(sedeService.listarActivas(negocio));
        }
        return ResponseEntity.ok(sedeService.listarActivas());
    }

    /** Listado completo para administración (incluye inactivas). */
    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN_GENERAL')")
    public ResponseEntity<List<SedeResponseDTO>> listarTodas() {
        return ResponseEntity.ok(sedeService.listarTodas());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN_GENERAL')")
    public ResponseEntity<SedeResponseDTO> crear(@Valid @RequestBody SedeRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sedeService.crear(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN_GENERAL')")
    public ResponseEntity<SedeResponseDTO> actualizar(@PathVariable String id, @Valid @RequestBody SedeRequestDTO dto) {
        return ResponseEntity.ok(sedeService.actualizar(id, dto));
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasRole('ADMIN_GENERAL')")
    public ResponseEntity<SedeResponseDTO> cambiarEstado(@PathVariable String id, @RequestParam("activa") boolean activa) {
        return ResponseEntity.ok(sedeService.cambiarEstado(id, activa));
    }
}
