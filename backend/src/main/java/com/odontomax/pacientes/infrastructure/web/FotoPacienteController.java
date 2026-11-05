package com.odontomax.pacientes.infrastructure.web;

import com.odontomax.pacientes.application.port.in.GestionarFotoPacienteUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/pacientes/{pacienteId}/foto")
@RequiredArgsConstructor
public class FotoPacienteController {

    private final GestionarFotoPacienteUseCase fotoUseCase;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO', 'RECEPCION')")
    public ResponseEntity<byte[]> obtener(@PathVariable UUID pacienteId) {
        return fotoUseCase.obtener(pacienteId)
                .map(f -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(f.contentType()))
                        .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePrivate())
                        .body(f.contenido()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO', 'RECEPCION')")
    public ResponseEntity<Map<String, Object>> guardar(@PathVariable UUID pacienteId,
                                                       @RequestParam("archivo") MultipartFile archivo) throws IOException {
        var actualizada = fotoUseCase.guardar(pacienteId, archivo.getBytes(), archivo.getContentType());
        return ResponseEntity.ok(Map.of("fotoActualizadaEn", String.valueOf(actualizada)));
    }

    @DeleteMapping
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO', 'RECEPCION')")
    public ResponseEntity<Void> eliminar(@PathVariable UUID pacienteId) {
        fotoUseCase.eliminar(pacienteId);
        return ResponseEntity.noContent().build();
    }
}
