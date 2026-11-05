package com.odontomax.tratamientos.web;

import com.odontomax.tratamientos.dto.PacienteSinPlanDTO;
import com.odontomax.tratamientos.service.PacientesSinPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/tratamientos/pacientes-sin-plan")
@RequiredArgsConstructor
public class PacientesSinPlanController {

    private final PacientesSinPlanService pacientesSinPlanService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO')")
    public ResponseEntity<List<PacienteSinPlanDTO>> listar(@RequestParam String sedeId) {
        return ResponseEntity.ok(pacientesSinPlanService.listar(sedeId));
    }
}
