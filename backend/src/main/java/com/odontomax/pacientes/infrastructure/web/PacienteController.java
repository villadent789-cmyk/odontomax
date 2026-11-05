package com.odontomax.pacientes.infrastructure.web;

import com.odontomax.identidad.api.SecurityUtils;
import com.odontomax.pacientes.application.dto.request.PacienteRequestDTO;
import com.odontomax.pacientes.application.dto.response.PacienteExportDTO;
import com.odontomax.pacientes.api.PacienteResumen;
import com.odontomax.pacientes.application.dto.response.PacienteResponseDTO;
import com.odontomax.pacientes.application.port.in.ActualizarPacienteUseCase;
import com.odontomax.pacientes.application.port.in.ArchivarPacienteUseCase;
import com.odontomax.pacientes.application.port.in.ConsultarPacienteUseCase;
import com.odontomax.pacientes.application.port.in.EliminarPacienteUseCase;
import com.odontomax.pacientes.application.port.in.RegistrarPacienteUseCase;
import com.odontomax.pacientes.application.port.in.ExportarExpedienteUseCase;
import com.odontomax.pacientes.application.port.in.GestionarSolicitudesCorreccionUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/pacientes")
@RequiredArgsConstructor
public class PacienteController {

    private final ConsultarPacienteUseCase consultarPaciente;
    private final RegistrarPacienteUseCase registrarPaciente;
    private final ActualizarPacienteUseCase actualizarPaciente;
    private final ArchivarPacienteUseCase archivarPaciente;
    private final EliminarPacienteUseCase eliminarPaciente;
    private final ExportarExpedienteUseCase exportarExpedienteUseCase;
    private final GestionarSolicitudesCorreccionUseCase correcciones;

    @GetMapping("/cumpleanos-manana")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'RECEPCION')")
    public ResponseEntity<List<PacienteResumen>> listarCumpleanerosManana(
            @RequestParam(required = false) String sedeId
    ) {
        String sedeEfectiva = SecurityUtils.resolverSedeEfectiva(sedeId);
        return ResponseEntity.ok(consultarPaciente.listarCumpleanerosDeManana(sedeEfectiva));
    }

    @GetMapping("/{id}/exportar")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE')")
    public ResponseEntity<PacienteExportDTO> exportarPaciente(@PathVariable UUID id) {
        return ResponseEntity.ok(exportarExpedienteUseCase.exportarExpedienteCompleto(id));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO', 'RECEPCION')")
    public ResponseEntity<?> listarPacientes(
            @RequestParam(required = false) String busqueda,
            @RequestParam(required = false) String sedeId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        String sedeEfectiva = SecurityUtils.resolverSedeEfectiva(sedeId);
        if (page != null) {
            int pageNum = Math.max(0, page);
            int pageSize = (size != null && size > 0 && size <= 100) ? size : 20;
            org.springframework.data.domain.Pageable pageable =
                    org.springframework.data.domain.PageRequest.of(pageNum, pageSize, org.springframework.data.domain.Sort.by("nombres").ascending());
            return ResponseEntity.ok(consultarPaciente.listarPaginado(busqueda, sedeEfectiva, pageable));
        }
        return ResponseEntity.ok(consultarPaciente.listar(busqueda, sedeEfectiva));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO', 'RECEPCION')")
    public ResponseEntity<PacienteResponseDTO> obtenerPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(consultarPaciente.obtenerPorId(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO', 'RECEPCION')")
    public ResponseEntity<PacienteResponseDTO> registrarPaciente(
            @jakarta.validation.Valid @RequestBody PacienteRequestDTO request
    ) {
        String sedeEfectiva = SecurityUtils.resolverSedeEfectiva(request.getSedeId());
        SecurityUtils.validarAccesoSede(sedeEfectiva);
        return ResponseEntity.status(HttpStatus.CREATED).body(registrarPaciente.registrar(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO', 'RECEPCION')")
    public ResponseEntity<PacienteResponseDTO> actualizarPaciente(
            @PathVariable UUID id,
            @jakarta.validation.Valid @RequestBody PacienteRequestDTO request
    ) {
        // nombre, DNI y fecha de nacimiento: solo el dueño los cambia directamente; el resto pide la corrección
        correcciones.exigirAutorizacionParaCambiarIdentidad(id, request.getDni(), request.getNombres(), request.getApellidos(), request.getFechaNacimiento());
        return ResponseEntity.ok(actualizarPaciente.actualizar(id, request));
    }

    @PostMapping("/{id}/archivar")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE')")
    public ResponseEntity<Map<String, String>> archivarPaciente(
            @PathVariable UUID id,
            @RequestBody Map<String, String> payload
    ) {
        archivarPaciente.archivar(id, payload.get("motivo"));
        return ResponseEntity.ok(Map.of("mensaje", "Paciente archivado con éxito conservando su historia clínica intacta"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN_GENERAL')")
    public ResponseEntity<Map<String, Object>> eliminarPaciente(
            @PathVariable UUID id,
            @RequestParam(value = "confirmacion", defaultValue = "false") boolean confirmacion,
            @RequestParam(value = "motivo", required = false) String motivo,
            @org.springframework.security.core.annotation.AuthenticationPrincipal com.odontomax.identidad.api.ClinicaUserPrincipal principal,
            jakarta.servlet.http.HttpServletRequest request
    ) {
        if (!confirmacion) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Se requiere confirmación explícita (confirmacion=true) para la eliminación permanente de la historia clínica y radiografías."
            );
        }
        String clientIp = SecurityUtils.getClientIp(request);
        eliminarPaciente.eliminar(id, principal, clientIp, motivo);
        return ResponseEntity.ok(Map.of(
                "status", 200,
                "mensaje", "Paciente, citas e imágenes eliminados exitosamente de la base de datos Supabase y Cloudflare R2."
        ));
    }
}
