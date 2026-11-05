package com.odontomax.plantillas.web;

import com.odontomax.plantillas.dto.DatosRegistroRequestDTO;
import com.odontomax.plantillas.dto.PlantillaTratamientoDTO;
import com.odontomax.plantillas.dto.RegistroPlantillaDTO;
import com.odontomax.plantillas.dto.RegistroPlantillaRequestDTO;
import com.odontomax.plantillas.service.PlantillasService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/plantillas")
@RequiredArgsConstructor
public class PlantillasController {

    private final PlantillasService plantillas;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO')")
    public ResponseEntity<List<PlantillaTratamientoDTO>> listar(@RequestParam(required = false) String negocio) {
        return ResponseEntity.ok(plantillas.listar(negocio));
    }

    @GetMapping("/servicio/{idONombre}")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO')")
    public ResponseEntity<PlantillaTratamientoDTO> deServicio(@PathVariable String idONombre) {
        return ResponseEntity.ok(plantillas.deServicio(idONombre));
    }

    @GetMapping("/registros/paciente/{pacienteId}")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO')")
    public ResponseEntity<List<RegistroPlantillaDTO>> registrosDelPaciente(@PathVariable UUID pacienteId) {
        return ResponseEntity.ok(plantillas.registrosDelPaciente(pacienteId));
    }

    @PostMapping("/registros")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO')")
    public ResponseEntity<RegistroPlantillaDTO> crear(@RequestBody @Valid RegistroPlantillaRequestDTO req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(plantillas.crear(req));
    }

    @PutMapping("/registros/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO')")
    public ResponseEntity<RegistroPlantillaDTO> guardar(@PathVariable UUID id, @RequestBody @Valid DatosRegistroRequestDTO req) {
        return ResponseEntity.ok(plantillas.guardarDatos(id, req.datos()));
    }

    @PostMapping("/registros/{id}/cerrar")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO')")
    public ResponseEntity<RegistroPlantillaDTO> cerrar(@PathVariable UUID id) {
        return ResponseEntity.ok(plantillas.cerrar(id));
    }
}
