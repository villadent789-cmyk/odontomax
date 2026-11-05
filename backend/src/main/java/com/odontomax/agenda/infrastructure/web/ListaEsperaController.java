package com.odontomax.agenda.infrastructure.web;

import com.odontomax.agenda.application.dto.request.EsperaHuecoRequestDTO;
import com.odontomax.agenda.application.dto.response.EsperaHuecoResponseDTO;
import com.odontomax.agenda.application.port.in.GestionarListaEsperaUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Lista de espera de huecos: la maneja recepción y la administración, no el odontólogo. */
@RestController
@RequestMapping("/citas/espera-hueco")
@RequiredArgsConstructor
public class ListaEsperaController {

    private final GestionarListaEsperaUseCase listaEspera;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'RECEPCION')")
    public ResponseEntity<EsperaHuecoResponseDTO> agregar(@Valid @RequestBody EsperaHuecoRequestDTO req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(listaEspera.agregar(req));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'RECEPCION')")
    public ResponseEntity<List<EsperaHuecoResponseDTO>> listar(@RequestParam(required = false) String sedeId) {
        return ResponseEntity.ok(listaEspera.listar(sedeId));
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'RECEPCION')")
    public ResponseEntity<EsperaHuecoResponseDTO> cambiarEstado(@PathVariable UUID id, @RequestBody Map<String, String> cuerpo) {
        return ResponseEntity.ok(listaEspera.cambiarEstado(id, cuerpo.get("estado")));
    }
}
