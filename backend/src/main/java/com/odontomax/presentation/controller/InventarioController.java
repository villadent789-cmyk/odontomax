package com.odontomax.presentation.controller;

import com.odontomax.application.dto.request.InventarioMovimientoRequestDTO;
import com.odontomax.application.dto.request.ProductoInventarioCreateRequestDTO;
import com.odontomax.application.dto.request.ProductoInventarioUpdateRequestDTO;
import com.odontomax.application.dto.request.TransferenciaCreateRequestDTO;
import com.odontomax.application.dto.response.InventarioMovimientoResponseDTO;
import com.odontomax.application.dto.response.ProductoInventarioResponseDTO;
import com.odontomax.application.dto.response.TransferenciaResponseDTO;
import com.odontomax.inventario.service.InventarioService;
import com.odontomax.identidad.api.ClinicaUserPrincipal;
import com.odontomax.identidad.api.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/inventario")
@RequiredArgsConstructor
public class InventarioController {

    private final InventarioService inventarioService;

    @GetMapping("/productos")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO', 'RECEPCION')")
    public ResponseEntity<List<ProductoInventarioResponseDTO>> listarProductos(
            @RequestParam(required = false) String sedeId,
            @RequestParam(required = false) Boolean alertaStockBajo
    ) {
        ClinicaUserPrincipal user = SecurityUtils.getCurrentUser();
        String sedeFiltro = sedeId;
        if (user != null && !"ADMIN_GENERAL".equalsIgnoreCase(user.rol())) {
            sedeFiltro = user.sedeId();
        }
        return ResponseEntity.ok(inventarioService.listarProductos(sedeFiltro, alertaStockBajo));
    }

    @GetMapping("/productos/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO', 'RECEPCION')")
    public ResponseEntity<ProductoInventarioResponseDTO> obtenerProducto(@PathVariable UUID id) {
        return ResponseEntity.ok(inventarioService.obtenerProductoPorId(id));
    }

    // El catálogo maestro de insumos es compartido por todas las sedes: solo la Administradora General lo modifica
    @PostMapping("/productos")
    @PreAuthorize("hasRole('ADMIN_GENERAL')")
    public ResponseEntity<ProductoInventarioResponseDTO> crearProducto(
            @Valid @RequestBody ProductoInventarioCreateRequestDTO req
    ) {
        ClinicaUserPrincipal user = SecurityUtils.getCurrentUser();
        return ResponseEntity.status(HttpStatus.CREATED).body(inventarioService.crearProducto(req, user));
    }

    @PutMapping("/productos/{id}")
    @PreAuthorize("hasRole('ADMIN_GENERAL')")
    public ResponseEntity<ProductoInventarioResponseDTO> actualizarProducto(
            @PathVariable UUID id,
            @Valid @RequestBody ProductoInventarioUpdateRequestDTO req
    ) {
        ClinicaUserPrincipal user = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(inventarioService.actualizarProducto(id, req, user));
    }

    /** Baja lógica: el insumo deja de listarse, pero se conservan sus movimientos y transferencias. */
    @DeleteMapping("/productos/{id}")
    @PreAuthorize("hasRole('ADMIN_GENERAL')")
    public ResponseEntity<Void> eliminarProducto(@PathVariable UUID id) {
        ClinicaUserPrincipal user = SecurityUtils.getCurrentUser();
        inventarioService.darDeBajaProducto(id, user);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/movimientos")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO', 'RECEPCION')")
    public ResponseEntity<InventarioMovimientoResponseDTO> registrarMovimiento(
            @Valid @RequestBody InventarioMovimientoRequestDTO req
    ) {
        ClinicaUserPrincipal user = SecurityUtils.getCurrentUser();
        SecurityUtils.validarAccesoSede(req.getSedeId());
        return ResponseEntity.status(HttpStatus.CREATED).body(inventarioService.registrarMovimiento(req, user));
    }

    @GetMapping("/movimientos")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO', 'RECEPCION')")
    public ResponseEntity<List<InventarioMovimientoResponseDTO>> listarMovimientos(
            @RequestParam(required = false) UUID productoId,
            @RequestParam(required = false) String sedeId
    ) {
        ClinicaUserPrincipal user = SecurityUtils.getCurrentUser();
        String sedeFiltro = sedeId;
        if (user != null && !"ADMIN_GENERAL".equalsIgnoreCase(user.rol())) {
            sedeFiltro = user.sedeId();
        }
        return ResponseEntity.ok(inventarioService.listarMovimientos(productoId, sedeFiltro));
    }

    @GetMapping("/transferencias")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO', 'RECEPCION')")
    public ResponseEntity<List<TransferenciaResponseDTO>> listarTransferencias(
            @RequestParam(required = false) String sedeId
    ) {
        ClinicaUserPrincipal user = SecurityUtils.getCurrentUser();
        String sedeFiltro = (user != null && !"ADMIN_GENERAL".equalsIgnoreCase(user.rol()))
                ? user.sedeId()
                : sedeId;
        return ResponseEntity.ok(inventarioService.listarTransferencias(sedeFiltro));
    }

    @PostMapping("/transferencias")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'RECEPCION')")
    public ResponseEntity<TransferenciaResponseDTO> solicitarTransferencia(
            @Valid @RequestBody TransferenciaCreateRequestDTO req
    ) {
        ClinicaUserPrincipal user = SecurityUtils.getCurrentUser();
        if (user != null && !"ADMIN_GENERAL".equalsIgnoreCase(user.rol())) {
            SecurityUtils.validarAccesoSede(req.getSedeOrigenId());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(inventarioService.solicitarTransferencia(req, user));
    }

    @PatchMapping("/transferencias/{id}/recibir")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE', 'RECEPCION')")
    public ResponseEntity<TransferenciaResponseDTO> recibirTransferencia(@PathVariable UUID id) {
        ClinicaUserPrincipal user = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(inventarioService.recibirTransferencia(id, user));
    }

    @PatchMapping("/transferencias/{id}/cancelar")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_SEDE')")
    public ResponseEntity<TransferenciaResponseDTO> cancelarTransferencia(@PathVariable UUID id) {
        ClinicaUserPrincipal user = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(inventarioService.cancelarTransferencia(id, user));
    }
}
