package com.odontomax.sedes.infrastructure.web;

import com.odontomax.sedes.dto.ServicioResponseDTO;
import com.odontomax.sedes.service.ServicioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

/**
 * Catálogo público para la web de reservas (sin autenticación, bajo /reservas-publicas/**).
 * Solo expone datos no sensibles: nunca correo, teléfono, COP ni usuario vinculado de los especialistas.
 */
@RestController
@RequestMapping("/reservas-publicas/catalogo")
@RequiredArgsConstructor
public class CatalogoPublicoController {

    private final ServicioService servicioService;

    @GetMapping("/servicios")
    public ResponseEntity<List<ServicioResponseDTO>> servicios(
            @org.springframework.web.bind.annotation.RequestParam(value = "negocio", required = false) String negocio
    ) {
        List<ServicioResponseDTO> servicios = servicioService.listarServiciosPublicos(negocio);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(servicios);
    }
}
