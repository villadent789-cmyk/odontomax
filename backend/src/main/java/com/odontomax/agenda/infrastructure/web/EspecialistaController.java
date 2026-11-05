package com.odontomax.agenda.infrastructure.web;

import com.odontomax.agenda.application.dto.request.EspecialistaRequestDTO;
import com.odontomax.agenda.application.dto.request.MetaDoctorRequestDTO;
import com.odontomax.agenda.application.dto.request.MiEspecialidadRequestDTO;
import com.odontomax.agenda.application.dto.response.MetaDoctorResponseDTO;
import com.odontomax.agenda.application.dto.request.HorarioEspecialistaRequestDTO;
import com.odontomax.agenda.application.dto.request.SillonesEspecialistaRequestDTO;
import com.odontomax.agenda.application.dto.response.DisponibilidadResponseDTO;
import com.odontomax.agenda.application.dto.response.EspecialistaResponseDTO;
import com.odontomax.agenda.application.dto.response.HorarioEspecialistaResponseDTO;
import com.odontomax.agenda.application.port.in.ConsultarDisponibilidadUseCase;
import com.odontomax.agenda.application.port.in.ConsultarEspecialistaUseCase;
import com.odontomax.agenda.application.port.in.GestionEspecialistaUseCase;
import com.odontomax.agenda.application.port.in.GestionHorariosEspecialistaUseCase;
import com.odontomax.identidad.api.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/especialistas")
@RequiredArgsConstructor
public class EspecialistaController {

    private final GestionEspecialistaUseCase gestionEspecialistaUseCase;
    private final ConsultarEspecialistaUseCase consultarEspecialistaUseCase;
    private final GestionHorariosEspecialistaUseCase gestionHorariosEspecialistaUseCase;
    private final ConsultarDisponibilidadUseCase consultarDisponibilidadUseCase;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO', 'RECEPCION')")
    public ResponseEntity<List<EspecialistaResponseDTO>> listarEspecialistas(
            @RequestParam(required = false) String sedeId
    ) {
        String sedeEfectiva = SecurityUtils.resolverSedeEfectiva(sedeId);
        if (sedeEfectiva != null && !sedeEfectiva.isBlank()) {
            SecurityUtils.validarAccesoSede(sedeEfectiva);
        }
        List<EspecialistaResponseDTO> especialistas = consultarEspecialistaUseCase.listarEspecialistas(sedeEfectiva);
        return ResponseEntity.ok(especialistas);
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN_GENERAL')")
    public ResponseEntity<List<EspecialistaResponseDTO>> listarTodos() {
        return ResponseEntity.ok(consultarEspecialistaUseCase.listarTodos());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN_GENERAL')")
    public ResponseEntity<EspecialistaResponseDTO> crear(
            @Valid @RequestBody EspecialistaRequestDTO dto
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(gestionEspecialistaUseCase.crear(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN_GENERAL')")
    public ResponseEntity<EspecialistaResponseDTO> actualizar(
            @PathVariable UUID id,
            @Valid @RequestBody EspecialistaRequestDTO dto
    ) {
        return ResponseEntity.ok(gestionEspecialistaUseCase.actualizar(id, dto));
    }

    @GetMapping("/{id}/meta")
    @PreAuthorize("hasRole('ADMIN_GENERAL')")
    public ResponseEntity<MetaDoctorResponseDTO> obtenerMeta(@PathVariable UUID id) {
        return ResponseEntity.ok(gestionEspecialistaUseCase.obtenerMeta(id));
    }

    @PatchMapping("/{id}/meta")
    @PreAuthorize("hasRole('ADMIN_GENERAL')")
    public ResponseEntity<MetaDoctorResponseDTO> actualizarMeta(
            @PathVariable UUID id,
            @Valid @RequestBody MetaDoctorRequestDTO dto
    ) {
        return ResponseEntity.ok(gestionEspecialistaUseCase.actualizarMeta(id, dto));
    }

    @PatchMapping("/{id}/sillones")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'RECEPCION')")
    public ResponseEntity<EspecialistaResponseDTO> actualizarSillones(
            @PathVariable UUID id,
            @Valid @RequestBody SillonesEspecialistaRequestDTO dto
    ) {
        return ResponseEntity.ok(gestionEspecialistaUseCase.actualizarSillones(id, dto));
    }

    @PatchMapping("/mi-especialidad")
    @PreAuthorize("hasRole('ODONTOLOGO')")
    public ResponseEntity<EspecialistaResponseDTO> actualizarMiEspecialidad(
            @Valid @RequestBody MiEspecialidadRequestDTO dto
    ) {
        return ResponseEntity.ok(gestionEspecialistaUseCase.actualizarMiEspecialidad(SecurityUtils.getCurrentUser().id(), dto.getEspecialidad()));
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasRole('ADMIN_GENERAL')")
    public ResponseEntity<EspecialistaResponseDTO> cambiarEstado(
            @PathVariable UUID id,
            @RequestParam("activo") boolean activo
    ) {
        return ResponseEntity.ok(gestionEspecialistaUseCase.cambiarEstado(id, activo));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN_GENERAL')")
    public ResponseEntity<Void> eliminar(@PathVariable UUID id) {
        gestionEspecialistaUseCase.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/horarios")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO', 'RECEPCION')")
    public ResponseEntity<List<HorarioEspecialistaResponseDTO>> obtenerHorarios(
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(gestionHorariosEspecialistaUseCase.obtenerHorarios(id));
    }

    @PutMapping("/{id}/horarios")
    @PreAuthorize("hasRole('ADMIN_GENERAL')")
    public ResponseEntity<List<HorarioEspecialistaResponseDTO>> reemplazarHorarios(
            @PathVariable UUID id,
            @RequestBody List<HorarioEspecialistaRequestDTO> horarios
    ) {
        return ResponseEntity.ok(gestionHorariosEspecialistaUseCase.reemplazarHorarios(id, horarios));
    }

    @GetMapping("/{id}/disponibilidad")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO', 'RECEPCION')")
    public ResponseEntity<DisponibilidadResponseDTO> obtenerDisponibilidad(
            @PathVariable UUID id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(required = false) String sedeId
    ) {
        String sedeEfectiva = SecurityUtils.resolverSedeEfectiva(sedeId);
        if (sedeEfectiva != null && !sedeEfectiva.isBlank()) {
            SecurityUtils.validarAccesoSede(sedeEfectiva);
        }
        return ResponseEntity.ok(consultarDisponibilidadUseCase.obtenerDisponibilidad(id, fecha, sedeEfectiva));
    }
}
