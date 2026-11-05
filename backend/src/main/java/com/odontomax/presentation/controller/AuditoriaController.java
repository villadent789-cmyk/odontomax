package com.odontomax.presentation.controller;

import com.odontomax.application.dto.response.AuditoriaResponseDTO;
import com.odontomax.infrastructure.persistence.entity.AuditoriaLogEntity;
import com.odontomax.infrastructure.persistence.repository.SpringDataAuditoriaRepository;
import com.odontomax.identidad.api.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/auditoria")
@RequiredArgsConstructor
public class AuditoriaController {

    private final SpringDataAuditoriaRepository auditoriaRepository;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN_GENERAL')")
    public ResponseEntity<List<AuditoriaResponseDTO>> listarAuditoria(
            @RequestParam(value = "modulo", required = false) String modulo
    ) {
        List<AuditoriaLogEntity> logs;
        if (modulo != null && !modulo.isBlank() && !modulo.equalsIgnoreCase("TODOS")) {
            logs = auditoriaRepository.findTop200ByModuloIgnoreCaseOrderByCreadoEnDesc(modulo.trim());
        } else {
            logs = auditoriaRepository.findTop200ByOrderByCreadoEnDesc();
        }

        List<AuditoriaResponseDTO> response = logs.stream()
                .map(AuditoriaResponseDTO::fromEntity)
                .toList();

        return ResponseEntity.ok(response);
    }
}
