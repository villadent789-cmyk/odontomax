package com.odontomax.sedes.infrastructure.web;

import com.odontomax.sedes.dto.ServicioResponseDTO;
import com.odontomax.sedes.service.ServicioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/servicios")
@RequiredArgsConstructor
public class ServicioController {

    private final ServicioService servicioService;

    /**
     * B-021: el catálogo de servicios es global (no depende de la sede), así que {@code sedeId} se acepta por
     * compatibilidad pero se ignora: no autoriza ni filtra, y un usuario sin sede asignada también puede leerlo.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO', 'RECEPCION')")
    public ResponseEntity<List<ServicioResponseDTO>> listarServicios(
            @RequestParam(required = false) String sedeId,
            @RequestParam(required = false) String negocio
    ) {
        List<ServicioResponseDTO> servicios = (negocio != null && !negocio.isBlank())
                ? servicioService.listarServicios(negocio)
                : servicioService.listarServicios();
        return ResponseEntity.ok(servicios);
    }

    /** Listado completo para administración (incluye inactivos). */
    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN_GENERAL')")
    public ResponseEntity<List<ServicioResponseDTO>> listarTodos() {
        return ResponseEntity.ok(servicioService.listarTodos());
    }

    @org.springframework.web.bind.annotation.PostMapping
    @PreAuthorize("hasRole('ADMIN_GENERAL')")
    public ResponseEntity<ServicioResponseDTO> crear(
            @jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody
            com.odontomax.sedes.dto.ServicioRequestDTO dto
    ) {
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(servicioService.crear(dto));
    }

    @org.springframework.web.bind.annotation.PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN_GENERAL')")
    public ResponseEntity<ServicioResponseDTO> actualizar(
            @org.springframework.web.bind.annotation.PathVariable String id,
            @jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody
            com.odontomax.sedes.dto.ServicioRequestDTO dto
    ) {
        return ResponseEntity.ok(servicioService.actualizar(id, dto));
    }

    @org.springframework.web.bind.annotation.PatchMapping("/{id}/estado")
    @PreAuthorize("hasRole('ADMIN_GENERAL')")
    public ResponseEntity<ServicioResponseDTO> cambiarEstado(
            @org.springframework.web.bind.annotation.PathVariable String id,
            @RequestParam("activo") boolean activo
    ) {
        return ResponseEntity.ok(servicioService.cambiarEstado(id, activo));
    }
}
