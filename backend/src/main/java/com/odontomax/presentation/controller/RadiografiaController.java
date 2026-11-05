package com.odontomax.presentation.controller;

import com.odontomax.application.service.RadiografiaService;
import com.odontomax.domain.model.Radiografia;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/radiografias")
@RequiredArgsConstructor
public class RadiografiaController {

    private final RadiografiaService radiografiaService;

    @GetMapping("/paciente/{pacienteId}")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO', 'RECEPCION')")
    public ResponseEntity<List<Radiografia>> listarPorPaciente(@PathVariable UUID pacienteId) {
        List<Radiografia> estudios = radiografiaService.listarPorPaciente(pacienteId);
        return ResponseEntity.ok(estudios);
    }

    /**
     * Genera una URL prefirmada temporal para visualización segura del estudio
     */
    @GetMapping("/{id}/url-firmada")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO', 'RECEPCION')")
    public ResponseEntity<Map<String, Object>> obtenerUrlFirmada(
            @PathVariable UUID id,
            @RequestParam(value = "minutos", defaultValue = "15") int minutos
    ) {
        int minutosValidados = Math.max(1, Math.min(minutos, 15));
        String urlFirmada = radiografiaService.obtenerUrlFirmada(id, minutosValidados);
        return ResponseEntity.ok(Map.of(
                "id", id,
                "urlFirmada", urlFirmada,
                "expiraEnMinutos", minutosValidados
        ));
    }

    /**
     * Descarga o visualiza directamente el archivo radiográfico binario
     */
    @GetMapping("/{id}/archivo")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO', 'RECEPCION')")
    public ResponseEntity<byte[]> obtenerArchivo(@PathVariable UUID id) {
        var archivo = radiografiaService.obtenerContenidoArchivo(id);
        if (archivo == null || archivo.contenido() == null || archivo.contenido().length == 0) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(archivo.contentType()))
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + archivo.nombreArchivo() + "\"")
                .body(archivo.contenido());
    }

    /**
     * Endpoint protegido para subir radiografía dental a bucket privado en Cloudflare R2
     * Solo Odontólogos y Administradores pueden subir estudios de salud
     */
    @PostMapping(value = "/subir", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ODONTOLOGO', 'ADMIN_SEDE')")
    public ResponseEntity<Radiografia> subirRadiografia(
            @RequestParam("pacienteId") UUID pacienteId,
            @RequestParam(value = "doctorId", required = false) UUID doctorId,
            @RequestParam("tipoEstudio") String tipoEstudio,
            @RequestParam(value = "piezaFdi", required = false) Integer piezaFdi,
            @RequestParam(value = "etapa", defaultValue = "INICIAL") String etapa,
            @RequestParam(value = "diagnostico", required = false) String diagnostico,
            @RequestParam(value = "diagnosticoRadiografico", required = false) String diagnosticoRadiografico,
            @RequestPart("archivo") MultipartFile archivo
    ) throws IOException {

        String diagFinal = diagnostico != null && !diagnostico.isBlank() ? diagnostico : diagnosticoRadiografico;

        Radiografia guardada = radiografiaService.subirRadiografia(
                pacienteId,
                doctorId,
                tipoEstudio,
                piezaFdi,
                etapa,
                diagFinal,
                archivo
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(guardada);
    }

    /**
     * Elimina físicamente el estudio (BD y R2) dejando registro en auditoría con el motivo.
     * Restringido a administradores; ADMIN_SEDE solo dentro de su sede.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE')")
    public ResponseEntity<Void> eliminarRadiografia(
            @PathVariable UUID id,
            @RequestParam("motivo") String motivo
    ) {
        radiografiaService.eliminarRadiografia(id, motivo);
        return ResponseEntity.noContent().build();
    }
}
