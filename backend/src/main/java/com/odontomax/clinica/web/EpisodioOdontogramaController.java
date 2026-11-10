package com.odontomax.clinica.web;

import com.odontomax.clinica.dto.AbrirEpisodioRequestDTO;
import com.odontomax.clinica.dto.CerrarEpisodioRequestDTO;
import com.odontomax.clinica.dto.EpisodioOdontogramaDTO;
import com.odontomax.clinica.service.EpisodioOdontogramaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/odontograma/pacientes/{pacienteId}/episodios")
@RequiredArgsConstructor
public class EpisodioOdontogramaController {

    private final EpisodioOdontogramaService episodioService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO', 'RECEPCION')")
    public ResponseEntity<List<EpisodioOdontogramaDTO>> listar(@PathVariable UUID pacienteId) {
        return ResponseEntity.ok(episodioService.listar(pacienteId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO', 'RECEPCION')")
    public ResponseEntity<EpisodioOdontogramaDTO> abrir(@PathVariable UUID pacienteId,
                                                        @Valid @RequestBody(required = false) AbrirEpisodioRequestDTO req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(episodioService.abrir(pacienteId, req));
    }

    @PostMapping("/{episodioId}/cerrar")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO', 'RECEPCION')")
    public ResponseEntity<EpisodioOdontogramaDTO> cerrar(@PathVariable UUID pacienteId, @PathVariable UUID episodioId,
                                                         @Valid @RequestBody(required = false) CerrarEpisodioRequestDTO req) {
        return ResponseEntity.ok(episodioService.cerrar(pacienteId, episodioId, req));
    }
}
